#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
SDK_TOOLS="${GSI_BUILD_TOOLS:-${SDK_ROOT}/build-tools/34.0.0}"
SDK_PLATFORM="${GSI_ANDROID_JAR:-${SDK_ROOT}/platforms/android-33/android.jar}"
GSI_JAVA="${GSI_JAVA_HOME:-${JAVA_HOME:-}}"
JAVAC="${GSI_JAVA:+$GSI_JAVA/bin/}javac"
KEYTOOL="${GSI_JAVA:+$GSI_JAVA/bin/}keytool"
for tool in d8 aapt zipalign apksigner; do
    [[ -x "$SDK_TOOLS/$tool" ]] || { echo "Missing Android build tool: $tool" >&2; exit 1; }
done
[[ -f "$SDK_PLATFORM" ]] || { echo 'Missing Android SDK platform 33' >&2; exit 1; }
python3 scripts/clean-build.py
mkdir -p build/classes build/dex
mapfile -d '' JAVA_SOURCES < <(find src -name '*.java' -print0 | sort -z)
"$JAVAC" --release 8 -encoding UTF-8 -classpath "$SDK_PLATFORM" -d build/classes "${JAVA_SOURCES[@]}"
mapfile -d '' JAVA_CLASSES < <(find build/classes -name '*.class' -print0 | sort -z)
"$SDK_TOOLS/d8" --min-api 28 --lib "$SDK_PLATFORM" --output build/dex "${JAVA_CLASSES[@]}"
"$SDK_TOOLS/aapt" package -f -M AndroidManifest.xml -S res -I "$SDK_PLATFORM" -F build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
(cd payload && zip -q -r ../build/unsigned.apk lib)
"$SDK_TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ "${GSI_SIGNING_MODE:-development}" == release ]]; then
    : "${GSI_RELEASE_KEYSTORE:?Release keystore required}"
    : "${GSI_KEYSTORE_PASSWORD:?Release keystore password required}"
    : "${GSI_KEY_PASSWORD:?Release key password required}"
    : "${GSI_KEY_ALIAS:?Release key alias required}"
    [[ -f "$GSI_RELEASE_KEYSTORE" ]] || { echo 'Release keystore not found' >&2; exit 1; }
    KEYSTORE="$GSI_RELEASE_KEYSTORE"
else
    [[ "${GSI_SIGNING_MODE:-development}" == development ]] || { echo 'Invalid signing mode' >&2; exit 1; }
    KEYSTORE=build/development.jks
    if [[ ! -f "$KEYSTORE" ]]; then
        "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storepass gsisms-development -keypass gsisms-development \
            -alias gsisms -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=GSI SMS Guard Development' >/dev/null 2>&1
    fi
    export GSI_KEYSTORE_PASSWORD=gsisms-development GSI_KEY_PASSWORD=gsisms-development GSI_KEY_ALIAS=gsisms
fi
"$SDK_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass env:GSI_KEYSTORE_PASSWORD --key-pass env:GSI_KEY_PASSWORD \
    --ks-key-alias "$GSI_KEY_ALIAS" --out build/GSI-SMS-Guard.apk build/aligned.apk
"$SDK_TOOLS/apksigner" verify --verbose build/GSI-SMS-Guard.apk
"$SDK_TOOLS/zipalign" -c 4 build/GSI-SMS-Guard.apk
