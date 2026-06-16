#!/bin/bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
SOUP="$DIR/soup-lang/target/release/soup-lang"
BUILDDIR="$DIR/build"

mkdir -p "$BUILDDIR"

"$SOUP" "$DIR/config.soup" -o "$BUILDDIR/config.lua"
"$SOUP" "$DIR/maitea.soup" -o "$BUILDDIR/maitea.lua"
"$SOUP" "$DIR/display.soup" -o "$BUILDDIR/display.lua"
"$SOUP" "$DIR/maifetch.soup" -o "$BUILDDIR/maifetch.lua"

lua -e "package.path = '$BUILDDIR/?.lua;' .. package.path" "$BUILDDIR/maifetch.lua" "$@"
