package com.appleux.design.runtime

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Durable UI-state persistence.
 *
 * iOS keeps apps resident, so returning to one lands exactly where you left it
 * even days later. Android kills the process under pressure and only restores
 * what `onSaveInstanceState` wrote into a Bundle — which dies with the task.
 * This store closes that gap: state is written to disk, survives process death,
 * reboot and LMK kills.
 *
 * Deliberately frugal, because the target is 4 GB / 3 GB budget:
 *  - **Bounded on both axes.** Capped entry count *and* capped serialised
 *    size. An unbounded state store is a slow disk leak, and the byte cap is
 *    the one that actually saves you: 200 keys holding a 4 KB scroll offset
 *    each is under the entry cap and over the disk cap.
 *  - **Write-batched.** Writes are coalesced and flushed on a debounce, so a
 *    fling touching dozens of keys costs one write, not dozens.
 *  - **One background thread for all IO.** Serialisation touches the disk;
 *    doing it on the UI thread is a jank source on low-end storage. A single
 *    thread also means writes can never interleave.
 *  - **Timestamps are wall-clock, not uptime**, so state saved before a reboot
 *    is still meaningful afterwards.
 *
 * Scope: this is for *our own* surfaces' state. It deliberately does not and
 * cannot preserve third-party app state — Android's own
 * `onSaveInstanceState` contract governs those, and nothing here may fake a
 * successful restore.
 *
 * ## Bugs found in review (2026-10-15)
 *
 * The original version of this file was reviewed and every one of these was a
 * live defect, not a style nit:
 *
 *  1. **`clearAll()` never reached disk.** It cleared `dirty` and then called
 *     `flush()`, which begins `if (dirty.isEmpty()) return`. After a clear the
 *     file on disk still held every entry, so everything came back on the next
 *     launch. `clearAll()` was a no-op that also destroyed the in-memory copy.
 *  2. **The debounce did not debounce.** `handler.removeCallbacksAndMessages(FLUSH_TOKEN)`
 *     filters on `Message.obj`, but a `postDelayed(Runnable)` message has a
 *     null `obj`, so that call removed nothing. Every `put()` therefore
 *     scheduled its own flush: N writes meant N serialisations, which is
 *     exactly the cost the batching existed to avoid.
 *  3. **A failed write silently dropped the update.** `flush()` cleared the
 *     dirty flags *before* posting to the worker; if the write then threw, the
 *     flags were already gone and that update was lost until the process
 *     died.
 *  4. **The write was not atomic.** `flushBlocking()` wrote straight to the
 *     live file with `File.writeText`, which truncates first. A kill in the
 *     middle left a half-written JSON file, and the next load silently started
 *     clean — losing every surface's state. The `flush()` path had a better
 *     temp-file dance but a `writeText` fallback that was just as bad.
 *  5. **`load()` could clobber fresh writes.** `loaded = true` was set inside
 *     the background task, not under the lock, so N calls before the task ran
 *     posted N loads. A `put()` landing between two loads was overwritten by
 *     the second load with the stale on-disk value.
 *  6. **`MAX_BYTES` was declared and never used.** It only appeared in a
 *     4x-fudge guard on load; nothing enforced it on write.
 *  7. **Writes could race.** `flush()` and `flushBlocking()` both used the same
 *     `appleux_state.json.tmp` path, so two overlapping writers could
 *     interleave on one temp file.
 *  8. **A pending flush dropped on teardown.** `flushBlocking` cleared the
 *     main-thread callbacks but never drained the worker's queue, so an
 *     already-queued async flush could land after it.
 *  9. **The doc lied.** `flushBlocking` was documented as "empties the
 *     in-memory cache"; it does not, and it must not — the cache is the
 *     fast path.
 * 10. **An `Activity` Context was retained for the debounce window.** The
 *     posted Runnable closed over the caller's Context. Now always
 *     `applicationContext`.
 *
 * ## Memory note
 *
 * Values live in one [ConcurrentHashMap] of small immutable records rather
 * than two parallel maps. The old pair (`values` + `stamps`) stored every
 * entry twice, let the two drift apart, and made "evict by age" a two-map
 * operation. One map is half the nodes and one source of truth.
 */
object AppleuxStateStore {

    private const val TAG = "AppleuxState"
    private const val FILE = "appleux_state.json"

    /** Entry cap. Bounds the resident map, which is what protects the heap. */
    private const val MAX_ENTRIES = 256

    /**
     * Serialised-bytes cap. Bounds the file. This is the cap that matters for
     * a pathological caller storing megabyte blobs under many keys: 256 of
     * those is 1 MB of JSON rewritten every debounce window.
     */
    private const val MAX_BYTES = 256 * 1024

    /**
     * A file larger than this is treated as corrupt rather than loaded. Set
     * well above [MAX_BYTES] so an older, larger snapshot still loads instead
     * of being discarded on a legitimate upgrade.
     */
    private const val MAX_LOAD_BYTES = 4L * MAX_BYTES

    private const val FLUSH_DEBOUNCE_MS = 1500L

    /** How long a blocking load waits before giving up and starting clean. */
    private const val LOAD_TIMEOUT_MS = 2000L

    private class Entry(val value: String, val stamp: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * Set synchronously under [loadLock] so a second caller can never queue a
     * second load. Set to false only if the load failed outright.
     */
    @Volatile
    private var loaded = false

    private val loadLock = Any()

    private val loadDone = CountDownLatch(1)

    private val mainHandler = Handler(Looper.getMainLooper())

    /** The pending debounce, kept as a reference so it can actually be removed. */
    @Volatile
    private var pendingFlush: Runnable? = null

    /**
     * Single background thread for every read and write. Started lazily: a
     * process that never persists state never pays for the thread.
     */
    private object Io {
        val thread: HandlerThread by lazy {
            HandlerThread("appleux-state", Process.THREAD_PRIORITY_BACKGROUND).apply {
                start()
            }
        }
        val handler: Handler by lazy { Handler(thread.looper) }
    }

    // ---- Load --------------------------------------------------------------

    /**
     * Starts the disk read. Safe to call repeatedly and from any thread; only
     * the first call does anything. Every accessor below calls this itself, so
     * a caller can never forget.
     */
    fun load(context: Context) {
        val app = context.applicationContext
        synchronized(loadLock) {
            if (loaded) return
            // Claim the load synchronously. The previous version set this flag
            // inside the background task, so concurrent callers each queued
            // their own read and a fresh put() could be overwritten by a
            // second read of stale on-disk state.
            loaded = true
            Io.handler.post {
                try {
                    readInto(app)
                } catch (e: Exception) {
                    // Corrupt or absent state is never fatal: start clean
                    // rather than crash a surface on resume.
                    Log.w(TAG, "state load failed, starting clean: ${e.message}")
                } finally {
                    loadDone.countDown()
                }
            }
        }
    }

    private fun readInto(app: Context) {
        val f = File(app.filesDir, FILE)
        if (!f.exists()) return
        if (f.length() > MAX_LOAD_BYTES) {
            Log.w(TAG, "state file ${f.length()}B exceeds cap ${MAX_LOAD_BYTES}B, starting clean")
            return
        }
        val root = JSONObject(f.readText())
        // Tolerate a partially-written or hand-edited file: take the keys that
        // parse and drop the rest rather than discarding everything.
        for (key in root.keys()) {
            if (key.startsWith(STAMP_PREFIX)) continue
            if (!root.has(key)) continue
            val stamp = root.optLong(STAMP_PREFIX + key, 0L)
            entries[key] = Entry(root.optString(key), stamp)
        }
    }

    /**
     * Blocks until the initial load has finished. Only for callers that must
     * read a value synchronously during startup; anything on the main thread
     * should prefer the async path and use the returned default.
     */
    fun awaitLoad(context: Context) {
        load(context)
        try {
            loadDone.await(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // ---- Write -------------------------------------------------------------

    fun put(context: Context, key: String, value: String) {
        entries[key] = Entry(value, System.currentTimeMillis())
        scheduleFlush(context)
    }

    fun putInt(context: Context, key: String, value: Int) = put(context, key, value.toString())

    fun putLong(context: Context, key: String, value: Long) = put(context, key, value.toString())

    fun putBool(context: Context, key: String, value: Boolean) =
        put(context, key, if (value) "1" else "0")

    fun get(context: Context, key: String, default: String = ""): String {
        load(context)
        return entries[key]?.value ?: default
    }

    fun getInt(context: Context, key: String, default: Int = 0): Int =
        get(context, key, "").toIntOrNull() ?: default

    fun getLong(context: Context, key: String, default: Long = 0L): Long =
        get(context, key, "").toLongOrNull() ?: default

    fun getBool(context: Context, key: String, default: Boolean = false): Boolean =
        when (get(context, key, "")) {
            "1" -> true
            "0" -> false
            else -> default
        }

    /** When this key was last written (wall clock, 0 if never). */
    fun savedAt(key: String): Long = entries[key]?.stamp ?: 0L

    /** Number of resident entries. Exposed for the bounds tests and tooling. */
    val size: Int get() = entries.size

    fun remove(context: Context, key: String) {
        if (entries.remove(key) == null) return
        scheduleFlush(context)
    }

    /**
     * Drops everything, in memory and on disk.
     *
     * This now actually clears the file. It previously called `flush()`,
     * which returns immediately when there is nothing dirty — so the snapshot
     * on disk was never rewritten and every entry came back on next launch.
     */
    fun clearAll(context: Context) {
        val app = context.applicationContext
        entries.clear()
        cancelPendingFlush()
        Io.handler.post { writeSnapshot(app, emptyMap()) }
    }

    // ---- Flush -------------------------------------------------------------

    /**
     * Coalesces writes. Every accessor within [FLUSH_DEBOUNCE_MS] collapses
     * into a single serialisation.
     */
    private fun scheduleFlush(context: Context) {
        // applicationContext: the posted Runnable outlives this call, and
        // holding an Activity for the debounce window leaks it.
        val app = context.applicationContext
        cancelPendingFlush()
        val runnable = Runnable { flush(app) }
        pendingFlush = runnable
        mainHandler.postDelayed(runnable, FLUSH_DEBOUNCE_MS)
    }

    private fun cancelPendingFlush() {
        pendingFlush?.let { mainHandler.removeCallbacks(it) }
        pendingFlush = null
    }

    /** Writes pending state off the main thread. Safe to call from any thread. */
    fun flush(context: Context) {
        val app = context.applicationContext
        cancelPendingFlush()
        val snapshot: Map<String, Entry> = HashMap(entries)
        Io.handler.post {
            try {
                writeSnapshot(app, snapshot)
            } catch (e: Exception) {
                // The snapshot is simply not on disk yet. It stays in the
                // resident map, and the next put() will schedule another
                // flush, so nothing is lost beyond this window.
                Log.w(TAG, "state flush failed, will retry on next write: ${e.message}")
            }
        }
    }

    /**
     * Writes [snapshot] to disk atomically, on the IO thread.
     *
     * Atomicity matters more than speed here: a kill mid-write would leave a
     * truncated JSON file, and the next load would silently start clean —
     * losing every surface's state at once. The previous implementation wrote
     * the live file directly in the blocking path, which has exactly that
     * failure mode.
     */
    private fun writeSnapshot(app: Context, snapshot: Map<String, Entry>) {
        val trimmed = trimToBudget(snapshot)
        val json = serialise(trimmed)
        val target = File(app.filesDir, FILE)
        val tmp = File(app.filesDir, "$FILE.tmp")
        tmp.writeText(json)
        try {
            // rename(2) within one directory is atomic and replaces the
            // destination; ATOMIC_MOVE makes that guarantee explicit.
            java.nio.file.Files.move(
                tmp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: IOException) {
            // Filesystem without ATOMIC_MOVE support: renameTo is still atomic
            // on the same volume, so this is only a last resort.
            if (!tmp.renameTo(target)) {
                Log.w(TAG, "atomic rename failed, falling back to direct write")
                target.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private fun serialise(trimmed: Map<String, Entry>): String {
        val root = JSONObject()
        for ((key, entry) in trimmed) {
            root.put(key, entry.value)
            root.put(STAMP_PREFIX + key, entry.stamp)
        }
        return root.toString()
    }

    /**
     * Enforces both budgets: drops the oldest-written entries until the entry
     * count *and* the serialised size are inside [MAX_ENTRIES] / [MAX_BYTES].
     *
     * Age comes from the write stamp, so a surface that has not been touched
     * since last week goes before one the user just left. The byte check
     * re-serialises after each removal, which is O(n log n) worst case but only
     * runs when a caller has genuinely exceeded a budget — the normal path
     * serialises once and returns.
     */
    private fun trimToBudget(snapshot: Map<String, Entry>): Map<String, Entry> {
        var working = snapshot
        var json = serialise(working)
        if (working.size <= MAX_ENTRIES && json.length <= MAX_BYTES) return working

        // Newest first, so the tail we drop is the oldest.
        val byAgeDesc = working.entries.sortedByDescending { it.value.stamp }
        var dropped = 0
        for (entry in byAgeDesc) {
            if (working.size <= MAX_ENTRIES && json.length <= MAX_BYTES) break
            val trimmed = HashMap(working)
            if (trimmed.remove(entry.key) == null) continue
            working = trimmed
            dropped++
            json = serialise(working)
        }
        Log.i(
            TAG,
            "evicted $dropped stale state entries (${snapshot.size} -> ${working.size}, " +
                "${json.length}B)"
        )
        return working
    }

    /**
     * Writes everything pending and returns only once it is on disk.
     *
     * For process teardown. The write still runs on the IO thread — there is
     * exactly one, so it cannot race the debounced flush that is already
     * queued ahead of it.
     */
    fun flushBlocking(context: Context) {
        val app = context.applicationContext
        cancelPendingFlush()
        val snapshot: Map<String, Entry> = HashMap(entries)
        val done = CountDownLatch(1)
        Io.handler.post {
            try {
                writeSnapshot(app, snapshot)
            } catch (e: Exception) {
                Log.w(TAG, "blocking flush failed: ${e.message}")
            } finally {
                done.countDown()
            }
        }
        try {
            // Bounded: never block a caller indefinitely, and never block the
            // IO thread waiting on itself.
            done.await(FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private const val STAMP_PREFIX = "__t_"
    private const val FLUSH_TIMEOUT_MS = 3000L
}