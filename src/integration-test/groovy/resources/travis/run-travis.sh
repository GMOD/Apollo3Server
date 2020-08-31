#!/bin/bash

EXIT_STATUS=0

echo "Running test $TEST_SUITE"

if [[ $TEST_SUITE == "apollo" ]]; then
  echo "Running unit tests $TEST_SUITE"
#  ./gradlew -Dgeb.env=chromeHeadless check || EXIT_STATUS=$? #
  ./grailsw test-app -unit  || EXIT_STATUS=$? #
fi
if [[ $TEST_SUITE == "python-apollo3-docker" ]]; then
  echo "Running docker python-apollo tests"
  set -ex
  ps -ef | grep neo4j
  git clone --single-branch --branch fix-all-exons --depth=1 https://github.com/galaxy-genome-annotation/python-apollo
  cd python-apollo
  export ARROW_GLOBAL_CONFIG_PATH=`pwd`/test-data/docker-apollo3-arrow.yml
  python3 --version
  python3 -m venv .venv
  . .venv/bin/activate
  python3 --version
  pip3 install nose
  pip3 install .
  echo "doing bootstrap with docker3"
  ./bootstrap_apollo.sh --docker3
  python3 setup.py nosetests
  killall java || true
fi
if [[ $TEST_SUITE == "python-apollo3-local" ]]; then
  echo "Running local python-apollo tests"
  set -ex
  ps -ef | grep neo4j
  echo "setting password 1"
  curl -H "Content-Type: application/json" -X POST -d '{"password":"testpass"}' -u neo4j:neo4j http://localhost:7474/user/neo4j/password
  echo "setting password 2"
  curl -v -X POST http://neo4j:neo4j@localhost:7474/user/neo4j/password -d"password=testpass"
  echo "setting password 3 - > starting grails"
  ./grailsw run-app &
  git clone --single-branch --branch fix-all-exons --depth=1 https://github.com/galaxy-genome-annotation/python-apollo
  cd python-apollo
  export ARROW_GLOBAL_CONFIG_PATH=`pwd`/test-data/local-apollo3-arrow.yml
  python3 --version
  python3 -m venv .venv
  . .venv/bin/activate
  python3 --version
  pip3 install nose
  pip3 install .
  echo "doing bootstrap with local3"
  ./bootstrap_apollo.sh --local3
  python3 setup.py nosetests
  killall java || true
fi

exit $EXIT_STATUS
