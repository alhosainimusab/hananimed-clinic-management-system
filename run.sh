#!/usr/bin/env sh
# Compile and run without Maven (needs JDK 17+). Usage: ./run.sh [data-dir]
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp out com.hananimed.ui.ClinicSystem "${1:-data}"
