#!/bin/sh -liex
# shell w/ login & interactive, exit if any command fails, log each command

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.."
PROJECT_DIR=$PWD

BUILDOS=$1
BUILDARCH=$2

mkdir -p target
rsync -avrt --delete ./native/ ./target/

# tkrzw dependency
cd ./target/tkrzw
./configure --enable-zlib
make -j4

# force static lib to be included in libjtkrzw
rm -f ./*.dylib

# these flags will only help the ./configure succeed for tokyocabinet-java
export TZDIR="$PWD"
export CPATH="$CPATH:$TZDIR"
export LIBRARY_PATH="$LIBRARY_PATH:$TZDIR"
# this helps alpine linux build
export PKG_CONFIG_PATH="$PKG_CONFIG_PATH:$TZDIR"

# tkrzw-java
cd ../tkrzw-java
./configure
make -j4

TARGET_LIB=libjtkrzw.dylib
strip -u -r ./$TARGET_LIB

OUTPUT_DIR="../../tkrzw-${BUILDOS}-${BUILDARCH}/src/main/resources/jne/${BUILDOS}/${BUILDARCH}"
cp ./$TARGET_LIB "$OUTPUT_DIR"