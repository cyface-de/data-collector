#!/bin/bash

if [ -z $KEYSTORE_TLS_PASSWORD ]; then
    KEYSTORE_TLS_PASSWORD="secret"
fi

if [ -z $KEYSTORE_JWT_PASSWORD ]; then
    KEYSTORE_JWT_PASSWORD="secret"
fi

if [ -e /app/secrets/keystore.tls.jks ]; then
	KEYSTORE_TLS="/app/secrets/keystore.tls.jks"
else 
	KEYSTORE_TLS="localhost.jks"	
fi

if [ -e /app/secrets/keystore.jwt.jceks ]; then
	KEYSTORE_JWT="/app/secrets/keystore.jwt.jceks"
else 
	KEYSTORE_JWT="keystore.jceks"
fi

echo "Loading keystore for TLS from: $KEYSTORE_TLS"
echo "Password for TLS keystore: $KEYSTORE_TLS_PASSWORD"
echo "Loading keystore for JWT from: $KEYSTORE_JWT"
echo "Password for JWT keystore: $KEYSTORE_JWT_PASSWORD"


java -jar collector-2.0.0-SNAPSHOT-fat.jar -conf "{\"keystore.jwt\":\"$KEYSTORE_JWT\",\"keystore.jwt.password\":\"$KEYSTORE_JWT_PASSWORD\",\"keystore.tls\":\"$KEYSTORE_TLS\",\"keystore.tls.password\":\"$KEYSTORE_TLS_PASSWORD\",\"metrics.enabled\":true,\"mongo.userdb\":{\"db_name\":\"cyface-user\",\"connection_string\":\"mongodb://mongo-user:27017\",\"data_source_name\":\"cyface-user\"},\"mongo.datadb\":{\"db_name\":\"cyface-data\",\"connection_string\":\"mongodb://mongo-data:27017\",\"data_source_name\":\"cyface-data\"}}"
