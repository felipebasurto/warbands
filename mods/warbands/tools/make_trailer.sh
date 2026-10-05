#!/usr/bin/env bash
# Cuts the 28 s trailer: opening card, the three film clips with their captions, end card, and the synthesized theme.
# Needs publish/videos/<stamp>_film-{war,melee,assault}.mp4 (scripts/shoot.sh), the titles from trailer_titles.py and the music
# from trailer_music.py. Usage: tools/make_trailer.sh 2026-10-05_12.44
set -euo pipefail
cd "$(dirname "$0")/../publish"
stamp="${1:?usage: make_trailer.sh <video stamp, e.g. 2026-10-05_12.44>}"
v="videos/${stamp}_film"
ffmpeg -y -loglevel error \
	-loop 1 -t 3 -framerate 20 -i trailer/card-open.png -i "$v-war.mp4" -i "$v-melee.mp4" -i "$v-assault.mp4" \
	-loop 1 -t 3 -framerate 20 -i trailer/card-end.png \
	-loop 1 -framerate 20 -i trailer/cap-war.png -loop 1 -framerate 20 -i trailer/cap-melee.png -loop 1 -framerate 20 -i trailer/cap-assault.png \
	-i trailer/warbands-theme.wav \
	-filter_complex "[0:v]fps=20,format=yuv420p,fade=t=out:st=2.5:d=0.5[a0];\
[1:v]fps=20,format=yuv420p[v1];[v1][5:v]overlay=0:0:shortest=1:enable='between(t,0.8,7.2)'[b1];\
[2:v]fps=20,format=yuv420p[v2];[v2][6:v]overlay=0:0:shortest=1:enable='between(t,0.8,7.2)'[b2];\
[3:v]fps=20,format=yuv420p[v3];[v3][7:v]overlay=0:0:shortest=1:enable='between(t,0.8,7.2)'[b3];\
[4:v]fps=20,format=yuv420p,fade=t=in:st=0:d=0.5[a4];\
[a0][b1]xfade=transition=fade:duration=0.5:offset=2.5[x1];[x1][b2]xfade=transition=fade:duration=0.5:offset=10[x2];[x2][b3]xfade=transition=fade:duration=0.5:offset=17.5[x3];[x3][a4]xfade=transition=fade:duration=0.5:offset=25[out]" \
	-map "[out]" -map 8:a -c:v libx264 -crf 20 -pix_fmt yuv420p -c:a aac -b:a 192k -shortest trailer/warbands-trailer.mp4
ffmpeg -y -loglevel error -i trailer/warbands-trailer.mp4 -c:v libx264 -crf 27 -preset slow -vf scale=1280:-2 -pix_fmt yuv420p -c:a aac -b:a 128k trailer/warbands-trailer-web.mp4
echo "wrote trailer/warbands-trailer.mp4 and trailer/warbands-trailer-web.mp4"
