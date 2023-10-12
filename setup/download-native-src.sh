#!/bin/bash

BASEDIR=$(dirname "$0")
PROJECT_DIR=$BASEDIR/..
source $BASEDIR/versions

echo "Downloading..."
echo " project dir: $PROJECT_DIR"
echo " tkrzw: $TKRZW_VERSION"
echo " tkrzw-java: $TKRZW_JAVA_VERSION"

mkdir -p ${PROJECT_DIR}/native
rm -Rf ${PROJECT_DIR}/native/tkrzw
rm -Rf ${PROJECT_DIR}/native/tkrzw-java

# https://dbmx.net/tkrzw/pkg/tkrzw-0.9.22.tar.gz
curl -O https://dbmx.net/tkrzw/pkg/tkrzw-${TKRZW_VERSION}.tar.gz
tar zxvf tkrzw-${TKRZW_VERSION}.tar.gz
mv tkrzw-${TKRZW_VERSION} ${PROJECT_DIR}/native/tkrzw
rm -f tkrzw-${TKRZW_VERSION}.tar.gz

# https://dbmx.net/tkrzw/pkg-java/tkrzw-java-0.1.28.tar.gz
curl -O https://dbmx.net/tkrzw/pkg-java/tkrzw-java-${TKRZW_JAVA_VERSION}.tar.gz
tar zxvf tkrzw-java-${TKRZW_JAVA_VERSION}.tar.gz
mv tkrzw-java-${TKRZW_JAVA_VERSION} ${PROJECT_DIR}/native/tkrzw-java
rm -f tkrzw-java-${TKRZW_JAVA_VERSION}.tar.gz

# now, we'll extract out the java code to our own maven source layout, along with tweaking it a bit for our customer loader
cp ${PROJECT_DIR}/native/tkrzw-java/*.java ${PROJECT_DIR}/tkrzw-api/src/main/java/tkrzw/
# remove all tests
rm -f ${PROJECT_DIR}/tkrzw-api/src/main/java/tkrzw/*Test.java
# replace all lines of code that loads the library with System.loadLibrary with our custom loader
sed -i 's/System.loadLibrary("jtkrzw");/CustomLoader.loadLibrary();/g' ${PROJECT_DIR}/tkrzw-api/src/main/java/tkrzw/*.java