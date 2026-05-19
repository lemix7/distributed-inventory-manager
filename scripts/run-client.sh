#!/bin/sh
set -e
FX="$HOME/Downloads/javafx-sdk-21.0.11/lib"
BIN="$(cd "$(dirname "$0")" && pwd)/bin"
java --module-path "$FX" --add-modules javafx.controls -cp "$BIN" InventoryClient
