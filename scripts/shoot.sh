#!/usr/bin/env bash
# Marketing photo shoot: spectator camera, no HUD, 1920x1080, real biomes (the shoot.* game-test scenarios).
# Usage: scripts/shoot.sh [mod-folder] [scenario ...]     default mod: warbands, default scenarios: shoot.*
# Pictures land in mods/<mod>/publish/photos/ (gitignored), named <time>_<subject>.png. Slow: run it in the background.
set -uo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
mod="${1:-warbands}"; [ $# -gt 0 ] && shift
if [ $# -eq 0 ]; then set -- 'shoot.*'; fi
before="$(ls -d "$root"/build/game-test-shots/"$mod"-*/ 2>/dev/null | wc -l)"
"$root/scripts/game-test.sh" "$mod" "$@"
after="$(ls -d "$root"/build/game-test-shots/"$mod"-*/ 2>/dev/null | wc -l)"
if [ "$after" -le "$before" ]; then echo "No new photo folder: nothing was photographed."; exit 1; fi
shots="$(ls -dt "$root"/build/game-test-shots/"$mod"-*/ 2>/dev/null | head -1)"
out="$root/mods/$mod/publish/photos"
mkdir -p "$out"
stamp="$(date +%Y-%m-%d_%H.%M)"
count=0
for f in "$shots"*shoot-*.png; do
	[ -f "$f" ] || continue
	name="$(basename "$f" | sed -E 's/^[0-9]+_(.*)\.png$/\1/; s/^warbands-//')"
	cp "$f" "$out/${stamp}_${name}.png"
	count=$((count + 1))
done
echo "Copied $count photos to $out"

# Film sequences (<name>-f0000.png...) become mp4 + gif in publish/videos/.
vids="$root/mods/$mod/publish/videos"
for first in "$shots"*-f0000.png; do
	[ -f "$first" ] || continue
	name="$(basename "$first" | sed -E 's/^[0-9]+_(.*)-f0000\.png$/\1/')"
	mkdir -p "$vids"
	ffmpeg -y -loglevel error -framerate 20 -pattern_type glob -i "$shots*_${name}-f*.png" -c:v libx264 -crf 20 -pix_fmt yuv420p "$vids/${stamp}_${name}.mp4"
	ffmpeg -y -loglevel error -framerate 20 -pattern_type glob -i "$shots*_${name}-f*.png" \
		-vf "fps=10,scale=640:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=64[p];[b][p]paletteuse=dither=bayer:diff_mode=rectangle" "$vids/${stamp}_${name}.gif"
	for n in 100 125 150; do
		still="$(printf '%s' "$shots"*_${name}-f0${n}.png)"
		[ -f "$still" ] && cp "$still" "$out/${stamp}_shoot-${name}-still${n}.png"
	done
	echo "Film: $vids/${stamp}_${name}.mp4 + .gif"
done
