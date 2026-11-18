#!/usr/bin/env bash

set -e

BASEDIR=$(cd -P "$(dirname "${BASH_SOURCE-$0}")/"; pwd -P)

cd ${BASEDIR}
mvn -pl jdbc -am clean package -DskipTests
