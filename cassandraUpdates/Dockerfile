FROM 627987680837.dkr.ecr.eu-central-1.amazonaws.com/prd/cassandra-cli:728055
COPY *.cql /cassandraUpdates/
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -XX:-OmitStackTraceInFastThrow -jar /cassandra-cli.jar -DcqlDir=/cassandraUpdates"]

