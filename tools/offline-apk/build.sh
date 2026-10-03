#!/usr/bin/env bash
# Builds a signed, installable Last Exit APK WITHOUT Google Maven or the Android SDK manager.
#
# Used to produce dist/LastExit-v1.0.apk in a sandbox where dl.google.com was blocked. Normal
# development should use Android Studio (./gradlew assembleDebug); this is the fallback.
#
# Needs: JDK 17+, Gradle 8.x on PATH, and Ubuntu/Debian packages: aapt dalvik-exchange apksigner zipalign
#   sudo apt-get install aapt dalvik-exchange apksigner zipalign
# The Android 14 (API 34) framework comes from Robolectric's android-all jar on Maven Central.
#
# Usage: tools/offline-apk/build.sh [output.apk]
#   KEYSTORE=path/to.keystore KS_PASS=... to sign with your own key (one is generated otherwise).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-$REPO/build/offline-apk}"
OUT_APK="${1:-$WORK/LastExit.apk}"
ANDROID_ALL_URL="https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar"
DX_JAR="${DX_JAR:-/usr/share/java/com.android.dx-10.0.0.jar}"
KEYSTORE="${KEYSTORE:-$WORK/offline.keystore}"
KS_PASS="${KS_PASS:-lastexit}"
APP_ID=com.lastexit.app
mkdir -p "$WORK"

for tool in aapt2 zipalign apksigner gradle java; do
  command -v "$tool" >/dev/null || { echo "Missing $tool (see header of this script)"; exit 1; }
done
[ -f "$DX_JAR" ] || { echo "Missing dx ($DX_JAR): apt-get install dalvik-exchange"; exit 1; }

# 1. android.jar for API 34: framework classes + resources.arsc, minus JDK packages and metadata.
ANDROID_JAR="$WORK/android-34.jar"
if [ ! -f "$ANDROID_JAR" ]; then
  echo "Fetching Android 14 framework from Maven Central..."
  curl -fsSL -o "$WORK/android-all.jar" "$ANDROID_ALL_URL"
  rm -rf "$WORK/android-all" && mkdir -p "$WORK/android-all"
  ( cd "$WORK/android-all" && unzip -q ../android-all.jar && rm -rf java javax/xml META-INF \
      && find . -name "*.uau" -delete && find . -name "*_compat_config.xml" -delete && zip -q -r -X "$ANDROID_JAR" . )
  rm -rf "$WORK/android-all" "$WORK/android-all.jar"
fi

# 2. Resources and manifest (AGP would inject the package attribute the same way).
GEN="$WORK/gen"; rm -rf "$GEN" "$WORK/dex" && mkdir -p "$GEN" "$WORK/dex"
sed "s#<manifest #<manifest package=\"$APP_ID\" #" "$REPO/app/src/main/AndroidManifest.xml" > "$WORK/AndroidManifest.xml"
aapt2 compile --dir "$REPO/app/src/main/res" -o "$WORK/res.zip"
aapt2 link -I "$ANDROID_JAR" --manifest "$WORK/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 2 --version-name 1.1 \
  --java "$GEN" --auto-add-overlay -o "$WORK/base.apk" "$WORK/res.zip"

# 3. Kotlin: :core and :app compiled as separate modules.
B="$HERE/builder"
gradle -q -p "$B" --project-cache-dir "$WORK/gradle-cache" \
  -Prepo="$REPO" -PandroidJar="$ANDROID_JAR" -PgenDir="$GEN" clean :app:classes :app:runtimeJars
APP_CLASSES="$B/app/build/classes"
CORE_CLASSES="$B/core/build/classes/kotlin/main"

# 4. Dex (the stdlib's Java 9 module-info is removed first; dx cannot parse it).
STDLIB="$WORK/kotlin-stdlib-dex.jar"
rm -rf "$WORK/stdlib" && mkdir -p "$WORK/stdlib"
( cd "$WORK/stdlib" && unzip -q "$(ls "$B"/app/build/runtime/kotlin-stdlib-*.jar)" && rm -rf META-INF/versions && rm -f "$STDLIB" && zip -q -r "$STDLIB" . )
java -jar "$DX_JAR" --dex --min-sdk-version=26 --output="$WORK/dex/classes.dex" \
  "$APP_CLASSES/kotlin/main" "$APP_CLASSES/java/main" "$CORE_CLASSES" "$STDLIB"

# 5. Package, align (resources.arsc stays stored and 4-byte aligned), sign with v2 + v3.
cp "$WORK/base.apk" "$WORK/unaligned.apk"
( cd "$WORK/dex" && zip -q -X "$WORK/unaligned.apk" classes.dex )
zipalign -f -p 4 "$WORK/unaligned.apk" "$WORK/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias lastexit \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Last Exit, O=CODEXIS Hackathon"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" --ks-key-alias lastexit \
  --min-sdk-version 26 --out "$OUT_APK" "$WORK/aligned.apk"
apksigner verify --min-sdk-version 26 "$OUT_APK"
echo "Built $OUT_APK"
