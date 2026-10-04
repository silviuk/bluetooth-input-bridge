#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_BUILD_TOOLS="/opt/android-sdk/build-tools/android-13"
ANDROID_JAR="/opt/android-sdk/platforms/android-13/android.jar"
BIN_DIR="${BASE_DIR}/../bin"
BUILD_DIR="${BASE_DIR}/build"

mkdir -p "${BIN_DIR}" "${BUILD_DIR}/gen" "${BUILD_DIR}/compiled_res" "${BUILD_DIR}/obj"

echo "=== 1. Compiling Android Resources with AAPT2 ==="
"${SDK_BUILD_TOOLS}/aapt2" compile --dir "${BASE_DIR}/app/src/main/res" -o "${BUILD_DIR}/compiled_res.zip"

echo "=== 2. Linking Resources and Generating R.java ==="
"${SDK_BUILD_TOOLS}/aapt2" link \
    -I "${ANDROID_JAR}" \
    --manifest "${BASE_DIR}/app/src/main/AndroidManifest.xml" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    --version-code 1 \
    --version-name "0.1.0" \
    --java "${BUILD_DIR}/gen" \
    -o "${BUILD_DIR}/unaligned.apk" \
    "${BUILD_DIR}/compiled_res.zip"

echo "=== 3. Compiling Java Source Files ==="
javac -source 11 -target 11 \
    -cp "${ANDROID_JAR}:${BUILD_DIR}/gen" \
    -d "${BUILD_DIR}/obj" \
    $(find "${BUILD_DIR}/gen" "${BASE_DIR}/app/src/main/java" -name "*.java")

echo "=== 4. Converting Bytecode to DEX with D8 ==="
"${SDK_BUILD_TOOLS}/d8" \
    --lib "${ANDROID_JAR}" \
    --output "${BUILD_DIR}" \
    $(find "${BUILD_DIR}/obj" -name "*.class")

echo "=== 5. Adding classes.dex to APK ==="
cd "${BUILD_DIR}"
zip -uj "${BUILD_DIR}/unaligned.apk" classes.dex
cd "${BASE_DIR}"

echo "=== 6. Aligning APK with zipalign ==="
rm -f "${BIN_DIR}/S24InputBridge-unsigned.apk" "${BIN_DIR}/S24InputBridge.apk"
"${SDK_BUILD_TOOLS}/zipalign" -v -p 4 "${BUILD_DIR}/unaligned.apk" "${BIN_DIR}/S24InputBridge-unsigned.apk"

echo "=== 7. Signing APK with apksigner ==="
KEYSTORE="${BUILD_DIR}/debug.keystore"
if [ ! -f "${KEYSTORE}" ]; then
    keytool -genkeypair -v \
        -keystore "${KEYSTORE}" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

"${SDK_BUILD_TOOLS}/apksigner" sign \
    --ks "${KEYSTORE}" \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "${BIN_DIR}/S24InputBridge.apk" \
    "${BIN_DIR}/S24InputBridge-unsigned.apk"

rm -f "${BIN_DIR}/S24InputBridge-unsigned.apk"

echo "=== SUCCESS! Android APK generated: ${BIN_DIR}/S24InputBridge.apk ==="
ls -lh "${BIN_DIR}/S24InputBridge.apk"
