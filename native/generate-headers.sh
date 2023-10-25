#!/bin/bash -lex

BASEDIR=$(dirname "$0")
PROJECT_DIR=$BASEDIR/..
cd "$PROJECT_DIR"

mkdir -p target/headers
mkdir -p target/temp-classes
javac -h target/headers/ -cp ~/.m2/repository/com/fizzed/jne/3.2.0/jne-3.2.0.jar -d target/temp-classes/ shmemj-api/src/main/java/com/fizzed/shmemj/*.java