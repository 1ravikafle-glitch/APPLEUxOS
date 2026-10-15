#!/bin/bash
# AppleUX Cuttlefish one-command bring-up.
#
# Handles every rootless workaround in one place:
#   1. tap devices must exist (one-time sudo by the user, persistent):
#        sudo ip tuntap add dev cvd-etap-01 mode tap user $USER
#        sudo ip tuntap add dev cvd-mtap-01 mode tap user $USER
#        sudo ip link set cvd-etap-01 up; sudo ip link set cvd-mtap-01 up
#   2. vhost ACLs (one-time sudo): setfacl -m u:$USER:rw /dev/{kvm,vhost-net,vhost-vsock}
#   3. cvdnetwork group (one-time sudo): groupadd -f cvdnetwork; usermod -aG kvm,cvdnetwork $USER
#   4. capability_query.py shim via LD_PRELOAD (no sudo ever)
#
# usage: cf-up.sh [memory_mb] [cpus]   (defaults: 3072 2)
# Afterwards: adb devices shows 0.0.0.0:6520 (via the vsock proxy below).

set -u

CFDIR="/home/elfakiris/OsProject/cuttlefish/cf"
SHIMROOT="/tmp/opencode/ios-insp/cfns/root"
SHIMSO="/tmp/opencode/ios-insp/cfns/shim.so"
MEM="${1:-3072}"
CPUS="${2:-2}"

for t in cvd-etap-01 cvd-mtap-01; do
  if ! ip link show "$t" >/dev/null 2>&1; then
    echo "MISSING tap $t — run the sudo block from this script's header first." >&2
    exit 1
  fi
done
[ -f "$SHIMSO" ] || { echo "MISSING shim $SHIMSO" >&2; exit 1; }

echo "==> launching Cuttlefish (${MEM}MB, ${CPUS} cpus)"
sg cvdnetwork -c "cd '$CFDIR' && CFNS_ROOT='$SHIMROOT' LD_PRELOAD='$SHIMSO' HOME='$CFDIR' ./bin/launch_cvd --daemon -vm_manager qemu_cli -memory_mb $MEM -cpus $CPUS -report_anonymous_usage_stats=n" 2>&1 | tail -n 4

echo "==> waiting for vsock adb bridge"
for i in $(seq 1 40); do
  if (ss -tlnp 2>/dev/null || netstat -tlnp 2>/dev/null) | grep -q ":6520"; then
    echo "    bridge up (0.0.0.0:6520)"
    break
  fi
  sleep 15
done

echo "==> starting manual vsock proxy (run_cvd-spawned ones are unreliable)"
cd "$CFDIR" && HOME="$PWD" ./bin/socket_vsock_proxy \
  --server_type=tcp --server_tcp_port=6520 \
  --client_type=vsock --client_vsock_port=5555 --client_vsock_id=3 \
  --label=adb >/tmp/opencode/ios-insp/cf-proxy.log 2>&1 &
sleep 3

echo "==> adb"
export PATH="/home/elfakiris/OsProject/android-sdk/platform-tools:$PATH"
adb connect localhost:6520 2>&1 | head -n 1
adb devices 2>&1 | head -n 5
echo
echo "CF up. Install apps, e.g.:"
echo "  adb -s 0.0.0.0:6520 install -r out/emulator/apks/appleux-launcher.apk"
