#!/bin/sh -le

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.." || exit 1
PROJECT_DIR=$PWD

USERID=$(id -u ${USER})
USERNAME=${USER}

DOCKER_IMAGE="$1"
CONTAINER_NAME="$2"
BUILDOS=$3
BUILDARCH=$4

# https://github.com/cross-rs/cross/tree/main/docker
# https://kerkour.com/rust-cross-compilation
# https://www.docker.com/blog/cross-compiling-rust-code-for-multiple-architectures/

DOCKERFILE="setup/Dockerfile.linux"
if [ ! -z "$(echo $DOCKER_IMAGE | grep "\-alpine")" ]; then
  DOCKERFILE="setup/Dockerfile.linux_musl"
fi

docker build -f "$DOCKERFILE" --progress=plain \
  --build-arg "FROM_IMAGE=${DOCKER_IMAGE}" \
  --build-arg USERID=${USERID} \
  --build-arg USERNAME=${USERNAME} \
  -t ${CONTAINER_NAME} \
  "$PROJECT_DIR/setup"