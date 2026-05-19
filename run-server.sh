#!/bin/sh
# Run the JavaFX server. The data/ directory is set as the working dir
# so the server picks up the *.txt inventory files generated there.
set -e
FX="$HOME/Downloads/javafx-sdk-21.0.11/lib"
BIN="$(cd "$(dirname "$0")" && pwd)/bin"
cd "$(dirname "$0")/data"
java --module-path "$FX" --add-modules javafx.controls -cp "$BIN" InventoryServer
