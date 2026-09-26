#!/bin/bash
# Desktop render harness: compiles the platform-independent game code against the android.graphics shim
# (Java2D) and runs a harness main class.   usage: scripts/harness.sh <MainClass> [args...]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
JAVA_HOME="$ROOT/tools/jdk-21.0.5+11"
OUT=build/harness; mkdir -p $OUT/classes
# everything except the Android-only view/activity code
SRC=$(find app/java -name '*.java' | grep -v -E '/(GameView|MainActivity|ScreenView|SafeArea|FullBleed|Screens|LayoutData|Elem|Hotspot)\.java$')
"$JAVA_HOME/bin/javac" -nowarn -encoding UTF-8 -d $OUT/classes $(find harness/shim -name '*.java') $SRC $(find harness/src -name '*.java') 2>&1 | grep -v "^Note:" || true
MAIN="$1"; shift
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp $OUT/classes "com.treasurerun.game.$MAIN" "$@" 2>&1 | grep -v "JAVA_TOOL_OPTIONS"
