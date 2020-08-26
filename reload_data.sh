#!/bin/bash
./stop_docker.sh  && rm -rf apollo_data/* && \
rm -rf $HOME/neo4j/data/*  && \
./start_docker.sh && \
./grailsw run-app
