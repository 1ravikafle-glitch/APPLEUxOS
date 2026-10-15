package com.appleux.performance.monitor

import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock

/**
 * CPU / GPU / thermal / memory / PSI / I-O telemetry.
 *
 * Cost model (this is the hot path on a Helio G99 / 4 GB target):
 *  - every kernel file is read through [CachedSource], so N consumers inside one
 *    sampling window cost one read, not N. Thermal alone walks ~30 zones on this
 *    SoC, so an uncached read was ~30 open/read/close per sample.
 *  - zone and cpufreq *paths* are enumerated once, lazily, and reused.
 *  - parsers are hand-written index scans: no `String.split(Regex)` (that
 *    recompiles the Pattern and allocates an ArrayList + String[] per call) and
 *    no per-sample String boxing.
 *  - readers are closed in a `finally`, so a malformed line cannot leak an fd.
 *  - /proc/stat is read once per sample and shared by the CPU and I/O readers.
 *
 * Nothing here writes to /proc or /sys: no LMKD interference, no thermal
 * throttling suppression, no clock forcing, no memory reservation. This class
 * is strictly an observer.
 */
class PressureMonitor {

    // ---------------------------------------------------------------- state --

    @Volatile var cpuUtilization: Float = 0f
        private set

    @Volatile var cpuFrequency: Float = 0f
        private set

    @Volatile var gpuUtilization: Float = 0f
        private set

    @Volatile var thermalState: ThermalState = ThermalState.NORMAL
        private set

    @Volatile var memoryPressure: Float = 0f
        private set

    @Volatile var ioWait: Float = 0f
        private set

    @Volatile var psiCpu: Float = 0f
        private set

    @Volatile var psiMemory: Float = 0f
        private set

    @Volatile var psiIo: Float = 0f
        private set

    /** Monotonic timestamp of the last successful full sample, or 0. */
    @Volatile var lastFullSampleMs: Long = 0L
        private set

    @Volatile var lastIoReadCount: Int = 0
        private set

    enum class ThermalState {
        NORMAL, WARM, HOT, THROTTLING, SEVERE
    }

    // --------------------------------------------------------------- update --

    /**
     * Full sample. Each source is only touched if its cache entry is older than
     * [ttlMs]; with the controller driving cadence from [MonitoringLevel] this
     * is a no-op when the tick is faster than the level's rate.
     */
    fun update(ttlMs: Long = DEFAULT_TTL_MS) {
        val now = SystemClock.elapsedRealtime()
        var reads = 0

        if (procStat(ttlMs, now)) reads++
        if (thermal(ttlMs, now)) reads++
        if (meminfo(ttlMs, now)) reads++
        if (psiCpu(ttlMs, now)) reads++
        if (psiMemory(ttlMs, now)) reads++
        if (psiIo(ttlMs, now)) reads++
        if (gpu(ttlMs, now)) reads++
        if (cpuFrequency(ttlMs, now)) reads++

        lastIoReadCount = reads
        lastFullSampleMs = now
    }

    /**
     * Cheap path for LEVEL_0_NORMAL. Memory and I/O are refreshed too: the
     * predicates below read them, and stale values from a previous full sample
     * (minutes old at 0.2 Hz) made L0 decisions depend on ancient data.
     */
    fun updateCpuLight(ttlMs: Long = LIGHT_TTL_MS) {
        val now = SystemClock.elapsedRealtime()
        var reads = 0

        if (procStat(ttlMs, now)) reads++
        if (thermal(ttlMs, now)) reads++
        if (meminfo(ttlMs, now)) reads++
        if (psiCpu(ttlMs, now)) reads++
        if (psiIo(ttlMs, now)) reads++

        lastIoReadCount = reads
        lastFullSampleMs = now
    }

    fun hasLightPressure(): Boolean =
        cpuUtilization > 80f || psiCpu > 40f ||
            thermalState != ThermalState.NORMAL ||
            memoryPressure > 80f || ioWait > 25f

    fun hasSeverePressure(): Boolean =
        cpuUtilization > 90f || psiCpu > 70f ||
            thermalState == ThermalState.SEVERE ||
            memoryPressure > 90f || ioWait > 35f ||
            psiMemory > 70f || psiIo > 70f

    /**
     * True when no reading is older than [maxAgeMs]. The controller refuses to
     * act on a cold cache after a long stop/start gap.
     */
    fun isFresh(maxAgeMs: Long = 30_000L): Boolean {
        val last = lastFullSampleMs
        return last > 0L && SystemClock.elapsedRealtime() - last <= maxAgeMs
    }

    // -------------------------------------------------------------- readers --

    /** One /proc/stat read feeding both CPU utilisation and iowait. */
    private fun procStat(ttlMs: Long, now: Long): Boolean {
        if (!statSource.needsRead(now, ttlMs)) return false
        val text = readTextOrNull(STAT_FILE) ?: return false

        var user = 0L
        var nice = 0L
        var system = 0L
        var idle = 0L
        var iowait = 0L
        var idx = 0
        val len = text.length
        // stop at the first newline: only the aggregate "cpu " line matters
        while (idx < len) {
            val ch = text[idx]
            if (ch == '\n' || ch == ' ') break
            idx++
        }
        var field = 0
        var acc = 0L
        var seenDigit = false
        while (idx < len) {
            val ch = text[idx]
            if (ch >= '0' && ch <= '9') {
                acc = acc * 10 + (ch - '0')
                seenDigit = true
            } else if (ch == '\n') {
                break
            } else if (ch == ' ') {
                if (seenDigit) {
                    when (field) {
                        0 -> user = acc
                        1 -> nice = acc
                        2 -> system = acc
                        3 -> idle = acc
                        4 -> iowait = acc
                    }
                    field++
                    acc = 0
                    seenDigit = false
                }
            } else if (ch == '\t') {
                continue
            } else {
                break
            }
            idx++
        }

        val totalAll = user + nice + system + idle + iowait
        if (totalAll > 0L) {
            val busy = user + nice + system
            cpuUtilization = (busy.toFloat() / totalAll.toFloat()) * 100f
            ioWait = (iowait.toFloat() / totalAll.toFloat()) * 100f
        } else {
            val totalNoWait = user + nice + system + idle
            if (totalNoWait > 0L) {
                val busy = user + nice + system
                cpuUtilization = (busy.toFloat() / totalNoWait.toFloat()) * 100f
            }
        }
        statSource.markRead(now)
        return true
    }

    private fun thermal(ttlMs: Long, now: Long): Boolean {
        if (!thermalSource.needsRead(now, ttlMs)) return false
        val zones = thermalZones()
        var maxMilliC = 0L
        var any = false
        for (i in zones.indices) {
            val milli = readLongOrNull(zones[i]) ?: continue
            any = true
            if (milli > maxMilliC) maxMilliC = milli
        }
        if (!any) return false
        val maxC = maxMilliC / 1000f
        thermalState = when {
            maxC >= 85f -> ThermalState.SEVERE
            maxC >= 75f -> ThermalState.HOT
            maxC >= 65f -> ThermalState.WARM
            else -> ThermalState.NORMAL
        }
        thermalSource.markRead(now)
        return true
    }

    private fun meminfo(ttlMs: Long, now: Long): Boolean {
        if (!meminfoSource.needsRead(now, ttlMs)) return false
        val text = readTextOrNull(MEMINFO_FILE) ?: return false

        var memTotal = -1L
        var memAvailable = -1L
        val totalIdx = text.indexOf("MemTotal:")
        if (totalIdx >= 0) memTotal = parseKbAfter(text, totalIdx + 9)
        if (memTotal < 0L) return false
        val availIdx = text.indexOf("MemAvailable:")
        if (availIdx >= 0) memAvailable = parseKbAfter(text, availIdx + 13)

        // pre-5 kernels have no MemAvailable; MemFree+Buffers+Cached is the
        // conservative stand-in
        if (memAvailable < 0L) {
            memAvailable = parseKbAfter(text, text.indexOf("MemFree:") + 8) +
                parseKbAfter(text, text.indexOf("Buffers:") + 8) +
                parseKbAfter(text, text.indexOf("Cached:") + 7)
        }

        memoryPressure = if (memAvailable in 0..memTotal) {
            ((memTotal - memAvailable).toFloat() / memTotal.toFloat()) * 100f
        } else {
            0f
        }
        meminfoSource.markRead(now)
        return true
    }

    private fun psiCpu(ttlMs: Long, now: Long): Boolean {
        val value = readPsi(PSI_CPU_FILE, psiCpuSource, ttlMs, now) ?: return false
        psiCpu = value
        return true
    }

    private fun psiMemory(ttlMs: Long, now: Long): Boolean {
        val value = readPsi(PSI_MEM_FILE, psiMemSource, ttlMs, now) ?: return false
        psiMemory = value
        return true
    }

    private fun psiIo(ttlMs: Long, now: Long): Boolean {
        val value = readPsi(PSI_IO_FILE, psiIoSource, ttlMs, now) ?: return false
        psiIo = value
        return true
    }

    /** null = cached, unreadable, or no avg10 line. */
    private fun readPsi(path: String, source: CachedSource, ttlMs: Long, now: Long): Float? {
        if (!source.needsRead(now, ttlMs)) return null
        val text = readTextOrNull(path) ?: return null
        val value = readAvg10(text)
        if (value < 0f) return null
        source.markRead(now)
        return value
    }

    private fun gpu(ttlMs: Long, now: Long): Boolean {
        if (!gpuSource.needsRead(now, ttlMs)) return false
        val value = readLongOrNull(GPU_BUSMON) ?: return false
        gpuUtilization = value.toFloat().coerceIn(0f, 100f)
        gpuSource.markRead(now)
        return true
    }

    private fun cpuFrequency(ttlMs: Long, now: Long): Boolean {
        if (!freqSource.needsRead(now, ttlMs)) return false
        val files = cpuFreqFiles()
        if (files.isEmpty()) return false
        var total = 0L
        var count = 0
        for (i in files.indices) {
            val khz = readLongOrNull(files[i]) ?: continue
            total += khz
            count++
        }
        if (count == 0) return false
        cpuFrequency = total.toFloat() / count / 1000f
        freqSource.markRead(now)
        return true
    }

    // ------------------------------------------------------- cached lookups --

    private fun thermalZones(): Array<String> {
        zoneLock.lock()
        try {
            val cached = zonePaths
            if (cached != null) return cached
            val dir = File(THERMAL_DIR)
            val children = dir.listFiles()
            if (children == null) {
                zonePaths = EMPTY
                return EMPTY
            }
            val out = ArrayList<String>(children.size)
            for (child in children) {
                val temp = File(child, "temp")
                if (temp.exists()) out.add(temp.absolutePath)
            }
            val arr = if (out.isEmpty()) EMPTY else out.toTypedArray()
            zonePaths = arr
            return arr
        } finally {
            zoneLock.unlock()
        }
    }

    private fun cpuFreqFiles(): Array<String> {
        freqLock.lock()
        try {
            val cached = freqPaths
            if (cached != null) return cached
            val dir = File(CPUFREQ_DIR)
            val children = dir.listFiles()
            if (children == null) {
                freqPaths = EMPTY
                return EMPTY
            }
            val out = ArrayList<String>(children.size)
            for (child in children) {
                if (!child.name.startsWith("policy")) continue
                val cur = File(child, "scaling_cur_freq")
                if (cur.exists()) out.add(cur.absolutePath)
            }
            val arr = if (out.isEmpty()) EMPTY else out.toTypedArray()
            freqPaths = arr
            return arr
        } finally {
            freqLock.unlock()
        }
    }

    // --------------------------------------------------------------- utils ---

    private fun readTextOrNull(path: String): String? {
        try {
            val f = File(path)
            if (!f.exists()) return null
            return f.readText()
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
    }

    private fun readLongOrNull(path: String): Long? {
        val text = readTextOrNull(path) ?: return null
        var acc = 0L
        var seen = false
        for (i in 0 until text.length) {
            val ch = text[i]
            if (ch in '0'..'9') {
                acc = acc * 10 + (ch - '0')
                seen = true
            } else if (seen) {
                break
            } else if (ch == '-' || ch == ' ') {
                continue
            } else {
                break
            }
        }
        return if (seen) acc else null
    }

    /** First unsigned integer at or after [from]; -1 when absent. */
    private fun parseKbAfter(text: String, from: Int): Long {
        if (from < 0 || from >= text.length) return -1L
        var acc = 0L
        var seen = false
        var i = from
        while (i < text.length) {
            val ch = text[i]
            if (ch in '0'..'9') {
                acc = acc * 10 + (ch - '0')
                seen = true
            } else if (seen) {
                break
            } else if (ch != ' ' && ch != '\t' && ch != ':') {
                break
            }
            i++
        }
        return if (seen) acc else -1L
    }

    /**
     * First `avg10=` value in a PSI file. PSI `avg10` is ALREADY a percentage
     * (0-100) per the kernel's pressure-stall-information docs, so it is not
     * rescaled: the previous `* 100f` saturated every sample at 100 and left
     * hasSeverePressure() permanently true on any kernel with PSI.
     *
     * -1 when the file predates PSI or has no avg10 line.
     */
    private fun readAvg10(text: String): Float {
        val idx = text.indexOf(AVG10)
        if (idx < 0) return -1f
        var whole = 0f
        var fraction = 0f
        var divisor = 1f
        var seenDigit = false
        var seenDot = false
        var i = idx + AVG10.length
        while (i < text.length) {
            val ch = text[i]
            if (ch in '0'..'9') {
                if (seenDot) {
                    fraction = fraction * 10f + (ch - '0')
                    divisor *= 10f
                } else {
                    whole = whole * 10f + (ch - '0')
                }
                seenDigit = true
                i++
                continue
            }
            if (ch == '.' && !seenDot && seenDigit) {
                seenDot = true
                i++
                continue
            }
            break
        }
        if (!seenDigit) return -1f
        val value = if (seenDot) whole + fraction / divisor else whole
        return value.coerceIn(0f, 100f)
    }

    // ------------------------------------------------------- cache support ---

    /** TTL gate + last-read stamp for one kernel file. Allocation-free. */
    private class CachedSource {
        @Volatile private var lastReadMs: Long = Long.MIN_VALUE

        fun needsRead(now: Long, ttlMs: Long): Boolean =
            lastReadMs == Long.MIN_VALUE || now - lastReadMs >= ttlMs

        fun markRead(now: Long) {
            lastReadMs = now
        }
    }

    companion object {
        private const val STAT_FILE = "/proc/stat"
        private const val MEMINFO_FILE = "/proc/meminfo"
        private const val PSI_CPU_FILE = "/proc/pressure/cpu"
        private const val PSI_MEM_FILE = "/proc/pressure/memory"
        private const val PSI_IO_FILE = "/proc/pressure/io"
        private const val THERMAL_DIR = "/sys/class/thermal"
        private const val CPUFREQ_DIR = "/sys/devices/system/cpu/cpufreq"
        private const val GPU_BUSMON = "/sys/class/kgsl/kgsl-busmon/kgsl_busmon_gpu_util"
        private const val AVG10 = "avg10="

        private val EMPTY = emptyArray<String>()

        /**
         * Thermal and cpufreq enumeration are the only expensive directory
         * walks; the rest default to a quarter-second so that even a mis-driven
         * tick cannot re-read the same file twice in a burst.
         */
        private const val DEFAULT_TTL_MS = 250L
        private const val LIGHT_TTL_MS = 1000L

        private val statSource = CachedSource()
        private val thermalSource = CachedSource()
        private val meminfoSource = CachedSource()
        private val psiCpuSource = CachedSource()
        private val psiMemSource = CachedSource()
        private val psiIoSource = CachedSource()
        private val gpuSource = CachedSource()
        private val freqSource = CachedSource()

        private val zoneLock = ReentrantLock()
        private val freqLock = ReentrantLock()

        @Volatile private var zonePaths: Array<String>? = null
        @Volatile private var freqPaths: Array<String>? = null
    }
}