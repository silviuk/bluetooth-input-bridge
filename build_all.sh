#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=========================================="
echo " Building Windows Application & Installer "
echo "=========================================="
make -C "${BASE_DIR}/windows" clean
make -C "${BASE_DIR}/windows"

echo "=========================================="
echo " Building Android Companion APK           "
echo "=========================================="
"${BASE_DIR}/android/build.sh"

echo "=========================================="
echo " All Artifacts Successfully Built!        "
echo "=========================================="
ls -lh "${BASE_DIR}/bin"
