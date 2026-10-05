#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=========================================="
echo " Building Lapdroid Windows App & Setup    "
echo "=========================================="
make -C "${BASE_DIR}/windows" clean
make -C "${BASE_DIR}/windows"

echo "=========================================="
echo " Building Lapdroid Android APK            "
echo "=========================================="
"${BASE_DIR}/android/build.sh"

echo "=========================================="
echo " Lapdroid Build Complete!                 "
echo "=========================================="
ls -lh "${BASE_DIR}/bin"
