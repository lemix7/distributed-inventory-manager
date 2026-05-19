#!/bin/sh
# Regenerate the inventory .txt files into data/. Uses no JavaFX.
set -e
BIN="$(cd "$(dirname "$0")" && pwd)/bin"
cd "$(dirname "$0")/data"
java -cp "$BIN" InventoryGenerator
