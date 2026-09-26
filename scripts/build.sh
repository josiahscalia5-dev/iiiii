#!/bin/bash
# usage: scripts/build.sh [versionCode] [versionName]   -> build/TreasureRun.apk
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
VC="${1:-4}"; VN="${2:-0.4 (rooms foundation)}"
export JAVA_HOME="$ROOT/tools/jdk-21.0.5+11"
rm -rf build && mkdir -p build/classes
"$JAVA_HOME/bin/javac" -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath tools/android-35.jar -d build/classes $(find app/java -name '*.java') 2>&1 | grep -v "^Note:" || true
test -f build/classes/com/treasurerun/game/GameView.class
java -cp tools/dx.jar com.android.dx.command.Main --dex --min-sdk-version=21 --output=build/classes.dex build/classes
cp -r app/apktool build/apk && cp build/classes.dex build/apk/ && cp -r app/assets build/apk/assets
sed -i "s/^  versionCode: .*/  versionCode: $VC/; s/^  versionName: .*/  versionName: $VN/" build/apk/apktool.yml
java -jar tools/apktool.jar b build/apk -o build/unsigned.apk >/dev/null
java -jar tools/uber.jar -a build/unsigned.apk -o build/signed --ks keystore/treasurerun-debug.jks --ksAlias treasurerun --ksPass treasurerun --ksKeyPass treasurerun >build/sign.log 2>&1 || { cat build/sign.log; exit 1; }
cp build/signed/*.apk build/TreasureRun.apk && ls -la build/TreasureRun.apk
