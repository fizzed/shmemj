#!/bin/bash -lex
# shell w/ login & interactive, exit if any command fails, log each command

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.."
PROJECT_DIR=$PWD

BUILDOS=$1
BUILDARCH=$2

cd native
cargo build --release

TARGET_LIB=libshmemj.so
#OUTPUT_DIR="../../tkrzw-${BUILDOS}-${BUILDARCH}/src/main/resources/jne/${BUILDOS}/${BUILDARCH}"
OUTPUT_DIR="../target/test-classes/jne/${BUILDOS}/${BUILDARCH}"
mkdir -p "$OUTPUT_DIR"
cp target/release/$TARGET_LIB "$OUTPUT_DIR"

#TARGET_LIB=libjtkrzw.so
#$STRIP ./$TARGET_LIB
#
#OUTPUT_DIR="../../tkrzw-${BUILDOS}-${BUILDARCH}/src/main/resources/jne/${BUILDOS}/${BUILDARCH}"
#cp ./$TARGET_LIB "$OUTPUT_DIR"

# Setup cross compile environment
#if [ -f /opt/setup-cross-build-environment.sh ]; then
#  source /opt/setup-cross-build-environment.sh $BUILDOS $BUILDARCH
#fi
#
#mkdir -p target
#rsync -avrt --delete ./native/ ./target/
#
## zlib dependency
#cd target
#tar zxvf /opt/zlib-1.2.13.tar.gz
#cd zlib-1.2.13
#./configure --prefix=$SYSROOT
#make
#make install
#cd ../../
#
#export CFLAGS="$CFLAGS -Wa,--noexecstack"
#export CXXFLAGS="$CXXFLAGS -Wa,--noexecstack"
#
## tkrzw dependency
#cd ./target/tkrzw
#./configure --host $BUILDTARGET --enable-zlib
#make -j4
#
## force static lib to be included in libjtkrzw
#rm -f ./*.so
#
## these flags will only help the ./configure succeed for tokyocabinet-java
#export TZDIR="$PWD"
#export CPATH="$CPATH:$TZDIR"
#export CXXFLAGS="$CXXFLAGS -I$TZDIR"
#export LDFLAGS="$LDFLAGS -L$TZDIR"
#export LIBRARY_PATH="$LIBRARY_PATH:$TZDIR"
#
## tkrzw-java
#cd ../tkrzw-java
#cp ../tkrzw/libtkrzw.a .
#./configure --host $BUILDTARGET
#make -j4
#
#TARGET_LIB=libjtkrzw.so
#$STRIP ./$TARGET_LIB
#
#OUTPUT_DIR="../../tkrzw-${BUILDOS}-${BUILDARCH}/src/main/resources/jne/${BUILDOS}/${BUILDARCH}"
#cp ./$TARGET_LIB "$OUTPUT_DIR"