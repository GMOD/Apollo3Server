
neo4j stop 
#rm -rf apollo_data/*
rm -rf /usr/local/neo4j/data/databases/graph.db
neo4j start
#./grailsw run-app  
./gradlew bootRun



