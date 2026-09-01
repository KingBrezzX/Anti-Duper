#!/usr/bin/env bash
set -euo pipefail

VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
JAR="target/BedrockAntiDupe-${VERSION}.jar"

test -f "$JAR"

CLASS_FILE="$(find target/classes -type f -name '*.class' | head -n 1)"
test -n "$CLASS_FILE"

MAJOR="$(javap -verbose "$CLASS_FILE" | awk '/major version/ {print $3; exit}')"
test "$MAJOR" = "69"

if find target/surefire-reports -type f -name '*.xml' -print0 | xargs -0 grep -Eq 'failures="[1-9]|errors="[1-9]'; then
  echo "FAILED: Surefire failures/errors detected."
  exit 1
fi

echo "Build verification: PASS"
echo "Version: $VERSION"
echo "Java class major: $MAJOR"
echo "JAR SHA-256:"
sha256sum "$JAR"

echo
echo "NOTE: Real-player exploit, actual power-loss, and third-party shop gates"
echo "are NOT marked passed by this script. They require their real staging environments."
