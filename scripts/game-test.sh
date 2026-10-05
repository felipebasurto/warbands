#!/usr/bin/env bash
# Run a mod's in-game checklist test and print a short report.
# Usage: scripts/game-test.sh <mod-folder> [scenario ...]
#   no scenarios: runs what's ticked in mods/<mod>/testing/checklist.md
#   scenarios:    ids or prefixes ending in * (e.g. raids.* battles.army-vs-raid), or "all"; the checklist is untouched
# The log goes to build/game-test-<mod>.log, the photos to build/game-test-shots/<mod>-<time>/. Runs of a minute or more: agents should start this in the background.
set -uo pipefail

if [ $# -lt 1 ]; then
	echo "usage: $0 <mod-folder> [scenario ...]" >&2
	exit 1
fi

mod="$1"; shift
root="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$root/mods/$mod/testing/checklist.md" ] || { echo "no mods/$mod/testing/checklist.md" >&2; exit 1; }
log="$root/build/game-test-$mod.log"
mkdir -p "$root/build"

only=""
if [ $# -gt 0 ]; then
	only="$(IFS=,; echo "$*")"
	echo "Running: $only"
else
	echo "Running the ticked items of mods/$mod/testing/checklist.md:"
	grep -E '^[[:space:]]*- \[[xX]\]' "$root/mods/$mod/testing/checklist.md" | sed -E 's/^[[:space:]]*- \[[xX]\] `?([a-z0-9_.-]+)`?.*/  \1/'
fi

start=$(date +%s)
(cd "$root" && GAMETEST_ONLY="$only" ./gradlew ":$mod:runClientGameTest" --console=plain) > "$log" 2>&1
status=$?
echo
echo "Finished in $(( $(date +%s) - start )) s (gradle exit $status). Log: $log"
echo
grep -E '\[gametest\] (PASS|FAIL|SUMMARY|PLAN)|\[gametest\] battle:' "$log" | sed -E 's/.*\[gametest\] //'
if ! grep -q '\[gametest\] SUMMARY' "$log"; then
	echo "No summary: the game didn't get through the run. Last lines:"
	grep -E 'AssertionError|Exception|error:|BUILD' "$log" | tail -15
fi
# The harness wipes its run folder at the start of every run: keep this run's photos in their own folder.
shots="$root/mods/$mod/build/run/clientGameTest/screenshots"
if [ -d "$shots" ] && [ -n "$(ls "$shots"/*.png 2>/dev/null)" ]; then
	keep="$root/build/game-test-shots/$mod-$(date +%Y%m%d-%H%M%S)"
	mkdir -p "$keep" && cp "$shots"/*.png "$keep"/
	echo; echo "Screenshots ($(ls "$keep" | wc -l | tr -d ' ')) kept in ${keep#$root/}/"
fi
exit $status
