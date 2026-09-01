#!/usr/bin/env bash
set -euo pipefail
# Real process-kill/restart smoke test. This is intentionally destructive only to the
# disposable staging server directory passed as $1. It is NOT a substitute for a host
# power-cut test; it verifies JVM-crash persistence/recovery behavior.
ROOT="${1:?staging directory required}"
PIDFILE="$ROOT/server.pid"
LOG="$ROOT/server.log"
if [[ ! -f "$PIDFILE" ]]; then echo "No server.pid; start the staging server first" >&2; exit 2; fi
PID=$(cat "$PIDFILE")
kill -KILL "$PID"
for i in {1..30}; do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
if kill -0 "$PID" 2>/dev/null; then echo "Server did not die" >&2; exit 3; fi
printf 'eula=true\n' > "$ROOT/eula.txt"
cd "$ROOT"
nohup java -Xms512M -Xmx1G -jar paper.jar --nogui >>"$LOG" 2>&1 &
echo $! > "$PIDFILE"
for i in {1..120}; do
  grep -q "Done" "$LOG" && break
  sleep 1
done
grep -q "Done" "$LOG"
! grep -qE 'Error occurred while enabling BedrockAntiDupe|Exception.*BedrockAntiDupe' "$LOG"
echo "POWER_LOSS_PROCESS_KILL_RECOVERY_SMOKE=PASS"
