#!/bin/bash
# Copyright 2018 Cyface GmbH
# 
# This file is part of the Cyface Data Collector.
#
#  The Cyface Data Collector is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#  
#  The Cyface Data Collector is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with the Cyface Data Collector.  If not, see <http://www.gnu.org/licenses/>.

if [ -z $KEYSTORE_TLS_PASSWORD ]; then
    KEYSTORE_TLS_PASSWORD="secret"
fi

if [ -e /app/secrets/keystore.tls.jks ]; then
    KEYSTORE_TLS="/app/secrets/keystore.tls.jks"
else 
    KEYSTORE_TLS="localhost.jks"	
fi

if [ -z $JWT_PRIVATE_KEY_FILE_PATH ]; then
    JWT_PRIVATE_KEY_FILE_PATH="/app/secrets/jwt/private_key.pem"
fi

if [  -z $JWT_PUBLIC_KEY_FILE_PATH ]; then
	JWT_PUBLIC_KEY_FILE_PATH="/app/secrets/jwt/public.pem"
fi

echo "Loading keystore for TLS from: $KEYSTORE_TLS"
echo "Password for TLS keystore: $KEYSTORE_TLS_PASSWORD"
echo "Loading keystore for JWT from: $KEYSTORE_JWT"
echo "Password for JWT keystore: $KEYSTORE_JWT_PASSWORD"

echo "Waiting for Database to start!"

MONGO_STATUS="not running"
COUNTER=0
while [ $COUNTER -lt 10 ] && [ "$MONGO_STATUS" = "not running" ]; do
    ((COUNTER++))
    echo "Try $COUNTER"
    nc -z mongo-data 27017
    if [ $? -eq 0 ]; then
	echo "Mongo Database is up!"
        MONGO_STATUS="running"
    else
        sleep 5s
    fi
done

if [ $COUNTER -ge 10 ]; then
    echo "Unable to find Mongo Database! API will not start!"
    exit 1
fi

echo "Starting API"
java -Dvertx.cacheDirBase=/tmp/vertx-cache -jar collector-2.0.0-SNAPSHOT-fat.jar -conf "{\"jwt.private\":\"$JWT_PRIVATE_KEY_FILE_PATH\",\"jwt.public\":\"$JWT_PUBLIC_KEY_FILE_PATH\",\"keystore.tls\":\"$KEYSTORE_TLS\",\"keystore.tls.password\":\"$KEYSTORE_TLS_PASSWORD\",\"metrics.enabled\":true,\"mongo.userdb\":{\"db_name\":\"cyface-user\",\"connection_string\":\"mongodb://mongo-user:27017\",\"data_source_name\":\"cyface-user\"},\"mongo.datadb\":{\"db_name\":\"cyface-data\",\"connection_string\":\"mongodb://mongo-data:27017\",\"data_source_name\":\"cyface-data\"}}"
