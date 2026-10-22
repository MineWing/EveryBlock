#!/bin/sh
# Renders the Modrinth page art to ../*.png with headless Chrome.
#   docs/modrinth/art/render.sh            (everything)
#   docs/modrinth/art/render.sh banner     (just the named pages)
set -e
cd "$(dirname "$0")"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
shot() { # name width height scale
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor="$4" --window-size="$2,$3" --virtual-time-budget=4000 \
    --screenshot="$PWD/../$1.png" "file://$PWD/$1.html?w=$2&h=$3" >/dev/null 2>&1
  echo "rendered ../$1.png"
}
want() { [ $# -eq 0 ] || echo " $ARGS " | grep -q " $1 "; }
ARGS="$*"
want icon && shot icon 512 512 1
want banner && shot banner 1280 360 1.5
for f in collect rarity finale history; do
  [ -f "feature-$f.html" ] && { [ -z "$ARGS" ] || want "feature-$f"; } && shot "feature-$f" 1280 720 1.5
done
exit 0
