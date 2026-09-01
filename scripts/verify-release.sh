#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-2.7.4}"
JAR="target/BedrockAntiDupe-${VERSION}.jar"

test -f "$JAR"
jar tf "$JAR" | grep -q '^plugin.yml$'
jar tf "$JAR" | grep -q 'xyz/zyrex/bedrockantidupe/BedrockAntiDupe.class'
jar tf "$JAR" | grep -q 'xyz/zyrex/bedrockantidupe/PaperRuntimeSelfTest.class'

MAJOR="$(javap -verbose -classpath "$JAR" xyz.zyrex.bedrockantidupe.BedrockAntiDupe | awk '/major version/ {print $3; exit}')"
test "$MAJOR" = "69"

echo "Release artifact verified: $JAR"
echo "Java class major: $MAJOR (Java 25)"
sha256sum "$JAR"
