#!/bin/bash
# Claude Tablet APK build Windows PC-n (Git Bash), Gradle nélkül.
# Kell: Android SDK (build-tools + platforms), Android Studio JBR (java/javac/keytool).
# Ugyanazzal a debug.keystore-ral ír alá, mint a tabletes build, így a meglévő app fölé telepíthető.
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_SDK:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
# ugyanaz az android.jar + r8.jar, mint a tabletes buildnél (a ../../projekt/sdk mappából), ha megvan
TSDK="${TSDK:-../../projekt/sdk}"
if [ -f "$TSDK/android.jar" ]; then JAR="$TSDK/android.jar"; else JAR="$SDK/platforms/$(ls "$SDK/platforms" | sort -V | tail -1)/android.jar"; fi
JBR="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
export PATH="$JBR/bin:$PATH"
MIN=24; API=33
APK=build/claude-tablet.apk

# Saját kulcs: a meglévő app fölé csak ugyanazzal a kulccsal lehet telepíteni (a repó nem tartalmazza).
if [ ! -f debug.keystore ]; then
    echo "Nincs debug.keystore – új kulcs generálása (a korábban telepített app fölé így nem települ)"
    keytool -genkeypair -noprompt -keystore debug.keystore -alias claudetablet -storepass android -keypass android         -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Claude Tablet, C=HU" 2>/dev/null
fi
rm -rf build; mkdir -p build/classes build/dex build/gen

echo "[1/6] aapt2 compile";  "$BT/aapt2.exe" compile --dir res -o build/res.zip
echo "[2/6] aapt2 link";     "$BT/aapt2.exe" link -o build/app.unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
                                 --min-sdk-version $MIN --target-sdk-version $API --java build/gen build/res.zip
echo "[3/6] javac";          javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$JAR" \
                                 -d build/classes $(find src build/gen -name '*.java')
echo "[4/6] d8"
if [ -f "$TSDK/r8.jar" ]; then D8=(java -cp "$TSDK/r8.jar" com.android.tools.r8.D8); else D8=("$BT/d8.bat"); fi
"${D8[@]}" --min-api $MIN --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && "$JBR/bin/jar.exe" uf ../app.unsigned.apk classes.dex)
echo "[5/6] zipalign + aláírás"
"$BT/zipalign.exe" -f 4 build/app.unsigned.apk build/app.aligned.apk
ALIAS=$(keytool -list -keystore debug.keystore -storepass android 2>/dev/null | awk -F, '/PrivateKeyEntry/{print $1; exit}')
"$BT/apksigner.bat" sign --ks debug.keystore --ks-key-alias "$ALIAS" --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version $MIN --out "$APK" build/app.aligned.apk
echo "[6/6] ellenőrzés";     "$BT/apksigner.bat" verify --min-sdk-version $MIN "$APK" && echo "      aláírás OK"
echo "Kész: $(pwd)/$APK"
