#!/bin/bash -lex
# shell w/ login & interactive, exit if any command fails, log each command

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.."
PROJECT_DIR=$PWD

BUILDOS=$1
BUILDARCH=$2

[[ -z "$BUILDOS" ]] && { echo "BUILDOS is empty" ; exit 1; }
[[ -z "$BUILDARCH" ]] && { echo "BUILDARCH is empty" ; exit 1; }

cd native
cargo build --release

OUTPUT_DIR="$PROJECT_DIR/shmemj-${BUILDOS}-${BUILDARCH}/src/main/resources/jne/${BUILDOS}/${BUILDARCH}"

if [ $BUILDOS = "macos" ]; then
  cp "$PROJECT_DIR"/native/target/release/*.dylib "$OUTPUT_DIR"
elif [ $BUILDOS = "windows" ]; then
  cp "$PROJECT_DIR"/native/target/release/*.dll "$OUTPUT_DIR"
else
  cp "$PROJECT_DIR"/native/target/release/*.so "$OUTPUT_DIR"
fi