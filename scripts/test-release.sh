#!/usr/bin/env bash
# One bounded run: regression assertions, actual MCP round trip, and rendered guide margins.
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
: > build/game-test-warbands.log
node scripts/test-mcpfabric.mjs > build/mcpfabric-release-client.log 2>&1 &
companion=$!
trap 'kill "$companion" 2>/dev/null || true' EXIT
ORG_GRADLE_PROJECT_mcpfabric=true scripts/game-test.sh warbands \
	release.mcp release.age-feedback release.camp-feedback release.cleanup release.terrain release.hoppers release.guards release.payout release.boss-loot release.reload gallery.guide lang
game_status=$?
wait "$companion"
mcp_status=$?
cat build/mcpfabric-release-client.log
python3 scripts/check-guide-margins.py mods/warbands/build/run/clientGameTest/screenshots
guide_status=$?
echo "Release checks: game=$game_status MCP=$mcp_status guide=$guide_status"
if (( game_status || mcp_status || guide_status )); then exit 1; fi
