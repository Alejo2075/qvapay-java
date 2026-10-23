#!/usr/bin/env bash
# Regenerates the browsable API reference (Javadoc) into ./apidocs from the public methods' doc comments.
# Usage: scripts/generate-apidocs.sh   then open apidocs/index.html
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp -q clean javadoc:javadoc
rm -rf apidocs
cp -R target/reports/apidocs apidocs
echo "API docs generated: $(pwd)/apidocs/index.html"
