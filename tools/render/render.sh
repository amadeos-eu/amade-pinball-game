#!/bin/sh
# Renders the pictures the game draws a table from, and puts them into the game's resources.
#   tools/render/render.sh <table id> [samples]
# Needs Python 3.13 with bpy 5.2.3, OpenEXR and numpy (see README.md); BLENDER_PY names that python.
set -e
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
id=${1:?table id, e.g. lantern-fair}
samples=${2:-128}
work=${WORK:-$repo/build/render}
"$repo/gradlew" -q -p "$repo" renderExport -Ptable="$id" -Pout="$work/$id"
"${BLENDER_PY:-python3}" "$here/passes.py" "$work/$id" "$id" "$work/out" "$samples"
dest="$repo/src/main/resources/eu/amadeos/games/pinball/render/$id"
mkdir -p "$dest"
rm -f "$dest"/*.png "$dest"/table.txt
cp "$work/out/$id"/*.png "$work/out/$id/table.txt" "$dest/"
echo "rendered $id into $dest"
