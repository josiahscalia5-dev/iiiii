#!/bin/bash
# Runs the Robolectric harness against the current source + assets.
# usage: harness/robolectric/run.sh [TestClass[#method]]   (needs scripts/build.sh to have produced build/TreasureRun.apk)
set -e
H="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$H/../.." && pwd)"
# rebuild the APK when sources/assets changed (Robolectric reads resources + assets from it)
if [ ! -f "$ROOT/build/TreasureRun.apk" ] || [ -n "$(find "$ROOT/app" -newer "$ROOT/build/TreasureRun.apk" -type f | head -1)" ]; then
  bash "$ROOT/scripts/build.sh" 99 "harness" >/dev/null 2>&1 || { bash "$ROOT/scripts/build.sh" 99 harness 2>&1 | grep -v JAVA_TOOL | tail -20; exit 1; }
fi
mkdir -p "$H/src/test/resources/com/android/tools" "$ROOT/build/harness" "$H/lib"
# androidx.test only ships as AARs on Google Maven; Robolectric needs their classes.jar
for a in androidx/test/monitor/1.7.2/monitor-1.7.2 androidx/test/espresso/espresso-idling-resource/3.6.1/espresso-idling-resource-3.6.1 androidx/tracing/tracing/1.1.0/tracing-1.1.0; do
  n=$(basename $a); [ -s "$H/lib/$n.jar" ] || { curl -sSfL -o "$H/lib/$n.aar" "https://dl.google.com/android/maven2/$a.aar" && (cd "$H/lib" && unzip -o -q $n.aar classes.jar && mv classes.jar $n.jar && rm $n.aar); }
done
cat > "$H/src/test/resources/com/android/tools/test_config.properties" <<P
android_merged_manifest=$ROOT/app/apktool/AndroidManifest.xml
android_merged_assets=$ROOT/app/assets
android_resource_apk=$ROOT/build/TreasureRun.apk
android_custom_package=com.treasurerun.game
P
cd "$H"
mvn -q -B -Dmaven.repo.local=/root/.m2/repository ${1:+-Dtest=$1} -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | grep -v "JAVA_TOOL_OPTIONS"
