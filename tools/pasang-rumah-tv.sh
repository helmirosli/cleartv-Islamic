#!/usr/bin/env bash
# Rumah TV: muat turun APK terkini dari GitHub Releases dan pasang pada TV, kemudian hantar tema.
#
# Guna:  bash tools/pasang-rumah-tv.sh            (TV di 192.168.68.104, tema dari theme-saya/)
#        TV_HOST=<ip> THEME=<folder> bash tools/pasang-rumah-tv.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:=192.168.68.104}"; export TV_HOST
REPO="${RUMAHTV_REPO:-helmirosli/cleartv-Islamic}"
THEME="${THEME:-theme-saya}"
PKG=com.rumahtv.home
NS=com.gathin.tvhome
mkdir -p backup

echo "1/4 Muat turun APK terkini..."
curl -fL --progress-bar -o backup/rumah-tv.apk \
  "https://github.com/$REPO/releases/download/rumah-tv-latest/rumah-tv.apk"

echo "2/4 Pasang dan jadikan Home..."
APK=backup/rumah-tv.apk launcher/build-and-install.sh

if [ -f "$THEME/config.json" ]; then
  echo "3/4 Hantar tema $THEME (waktu solat, azan, gambar)..."
  tools/theme.sh "$THEME"
else
  echo "3/4 Tiada $THEME/config.json: tema dilangkau (waktu solat belum aktif)."
fi

echo "4/4 Screensaver Islamik..."
tools/venv/bin/python -I tools/tv.py "settings put secure screensaver_components $PKG/$NS.ClockDream" >/dev/null

echo "Siap. Tekan Home pada remote."
