#!/bin/sh

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.." || exit 1

java -jar blaze.jar "$@"