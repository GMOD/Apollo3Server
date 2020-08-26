#!/bin/bash

EXIT_STATUS=0

echo "Running test $TEST_SUITE"

if [[ $TEST_SUITE == "apollo" ]]; then
  echo "Running unit tests $TEST_SUITE"
#  ./gradlew -Dgeb.env=chromeHeadless check || EXIT_STATUS=$? #
  ./grailsw test-app -unit  || EXIT_STATUS=$? #
fi
if [[ $TEST_SUITE == "python-apollo" ]]; then
  set -ex
#  cp src/integration-test/groovy/resources/travis/python-apollo.travis apollo-config.groovy
  ./grailsw run-app &
  git clone --single-branch --branch fix-all-exons --depth=1 https://github.com/galaxy-genome-annotation/python-apollo
  cd python-apollo
#  sed -i 's|8888|8080/apollo|' `pwd`/test-data/local-apollo3-arrow.yml
#  ARROW_GLOBAL_CONFIG_PATH=`pwd`/test-data/local-apollo3-arrow.yml
  ARROW_GLOBAL_CONFIG_PATH=`pwd`/test-data/docker-apollo3-arrow.yml
  export ARROW_GLOBAL_CONFIG_PATH
  python3 --version
  python3 -m venv .venv
#  apt-get update
#  apt-get install python3-venv
  . .venv/bin/activate
  python3 --version
  pip3 install .
  pip3 install nose
  ./bootstrap_apollo.sh --docker3
  python3 setup.py nosetests
  killall java || true
fi

exit $EXIT_STATUS
