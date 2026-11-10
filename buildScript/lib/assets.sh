#!/bin/bash

DIR=app/src/main/assets/sing-box
mkdir -p $DIR
cd $DIR

if [ -f "geoip.db.xz" ] && [ -f "geosite.db.xz" ] && [ -f "geoip.version.txt" ] && [ -f "geosite.version.txt" ]; then
  echo "Using pre-bundled geoip.db.xz ($(cat geoip.version.txt)) and geosite.db.xz ($(cat geosite.version.txt))"
  exit 0
fi

set -e

get_latest_release() {
  curl -Ls -o /dev/null -w %{url_effective} "https://github.com/$1/releases/latest" | awk -F/ '{print $NF}'
}

####
VERSION_GEOIP=`get_latest_release "SagerNet/sing-geoip"`
echo VERSION_GEOIP=$VERSION_GEOIP
echo -n $VERSION_GEOIP > geoip.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geoip/releases/latest/download/geoip.db
xz -9 -f geoip.db

####
VERSION_GEOSITE=`get_latest_release "SagerNet/sing-geosite"`
echo VERSION_GEOSITE=$VERSION_GEOSITE
echo -n $VERSION_GEOSITE > geosite.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geosite/releases/latest/download/geosite.db
xz -9 -f geosite.db
