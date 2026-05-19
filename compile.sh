#!/bin/sh
# Compile all sources into bin/ using the JavaFX SDK modules.
set -e
FX="$HOME/Downloads/javafx-sdk-21.0.11/lib"
mkdir -p bin
javac --module-path "$FX" --add-modules javafx.controls -d bin src/*.java
echo "Compiled to bin/"
