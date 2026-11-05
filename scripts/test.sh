#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
GSI_JAVA="${GSI_JAVA_HOME:-${JAVA_HOME:-}}"
mkdir -p build/tests
"${GSI_JAVA:+$GSI_JAVA/bin/}javac" --release 8 -d build/tests src/org/gsisms/guard/PollState.java tests/org/gsisms/guard/PollStateTest.java
"${GSI_JAVA:+$GSI_JAVA/bin/}java" -cp build/tests org.gsisms.guard.PollStateTest
python3 scripts/validate.py
