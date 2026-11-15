#!/bin/sh
# Claude Tablet APK build (PRoot Ubuntu aarch64, Gradle nelkul).
#   apt install openjdk-21-jdk-headless aapt apksigner zipalign
set -e
cd "$(dirname "$0")"

API=33
MIN=24
SDK=/root/projekt/sdk            # android.jar + r8.jar (a projekt/build.sh tolti le)
KS=/root/projekt/debug.keystore  # kozos debug kulcs
APK="build/claude-tablet.apk"

missing=""
for t in javac java aapt2 apksigner zipalign python3; do
    command -v "$t" >/dev/null 2>&1 || missing="$missing $t"
done
if [ -n "$missing" ]; then
    echo "Hianyzo eszkoz:$missing"
    echo "Telepitsd:  apt install openjdk-21-jdk-headless aapt apksigner zipalign"
    exit 1
fi
[ -f "$SDK/android.jar" ] && [ -f "$SDK/r8.jar" ] || {
    echo "Hianyzik $SDK/android.jar vagy r8.jar - futtasd elobb: sh /root/projekt/build.sh"; exit 1; }
if [ ! -f debug.keystore ]; then
    if [ -f "$KS" ]; then cp "$KS" debug.keystore; else
        keytool -genkeypair -noprompt -keystore debug.keystore -alias claudetablet \
            -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Claude Tablet, C=HU" 2>/dev/null
    fi
fi
[ -f res/mipmap-mdpi/ic_launcher.png ] || python3 gen_icons.py

rm -rf build
mkdir -p build/classes build/dex build/gen

echo "[1/6] Eroforrasok (aapt2 compile)..."
aapt2 compile --dir res -o build/res.zip

echo "[2/6] Linkeles + R.java (aapt2 link)..."
aapt2 link -o build/app.unsigned.apk \
    -I "$SDK/android.jar" --manifest AndroidManifest.xml \
    --min-sdk-version $MIN --target-sdk-version $API \
    --java build/gen \
    build/res.zip

echo "[3/6] Java forditas..."
javac -nowarn -encoding UTF-8 -bootclasspath "$SDK/android.jar" -source 8 -target 8 \
    -d build/classes $(find src build/gen -name '*.java') 2>&1 \
    | grep -viE 'bootstrap|source value|target value|deprecat|unchecked|^Note:|^[0-9]+ warnings?$|To suppress' || true
[ -n "$(find build/classes -name MainActivity.class)" ] || { echo "Forditasi hiba"; exit 1; }

echo "[4/6] Dexeles (d8)..."
java -cp "$SDK/r8.jar" com.android.tools.r8.D8 \
    --min-api $MIN --lib "$SDK/android.jar" \
    --output build/dex $(find build/classes -name '*.class')
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/app.unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex', 'classes.dex')
PY

echo "[5/6] Igazitas + alairas..."
zipalign -f 4 build/app.unsigned.apk build/app.aligned.apk
KEYALIAS=$(keytool -list -keystore debug.keystore -storepass android 2>/dev/null | awk -F, '/PrivateKeyEntry/{print $1; exit}')
apksigner sign --ks debug.keystore --ks-key-alias "$KEYALIAS" --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version $MIN --out "$APK" build/app.aligned.apk

echo "[6/6] Ellenorzes..."
apksigner verify --min-sdk-version $MIN "$APK" && echo "      alairas OK"

cp "$APK" /data/data/com.termux/files/home/ 2>/dev/null && \
    echo "      masolat: Termux home ~/claude-tablet.apk"

echo
echo "Kesz: $(pwd)/$APK ($(du -h "$APK" | cut -f1))"
