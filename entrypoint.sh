#!/bin/bash
# Copyright 2018,2019 Cyface GmbH
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

if [ -z $JWT_PRIVATE_KEY_FILE_PATH ]; then
    JWT_PRIVATE_KEY_FILE_PATH="/app/secrets/jwt/private_key.pem"
fi

if [  -z $JWT_PUBLIC_KEY_FILE_PATH ]; then
    JWT_PUBLIC_KEY_FILE_PATH="/app/secrets/jwt/public.pem"
fi

echo "Loading private key for JWT from: $JWT_PRIVATE_KEY_FILE_PATH"
echo "Loading public key for JWT from: $JWT_PUBLIC_KEY_FILE_PATH"

if [ -z $CYFACE_API_PORT ]; then
	CYFACE_API_PORT="8080"
fi

if [ -z $CYFACE_API_HOST ]; then
	CYFACE_API_HOST="localhost"
fi

if [ -z $CYFACE_API_ENDPOINT ]; then
	CYFACE_API_ENDPOINT="/api/v2/"
fi

echo "Running Cyface Collector API at $CYFACE_API_HOST:$CYFACE_API_PORT$CYFACE_API_ENDPOINT"

if [ -z $CYFACE_MANAGEMENT_PORT ]; then
	CYFACE_MANAGEMENT_PORT="13371"
fi

echo "Running Cyface Management API at port $CYFACE_MANAGEMENT_PORT"

if [ -z $ADMIN_USER ]; then
    echo "Unable to find admin user. Please set the environment variable ADMIN_USER to an appropriate value! API will not start!"
    exit 1
fi

if [ -z $ADMIN_PASSWORD ]; then
    echo "Unable to find admin password. Please set the environment variable ADMIN_PASSWORD to an appropriate value! API will not start!"
    exit 1
fi

if [ -z $METRICS_ENABLED ]; then
	METRICS_ENABLED="false"
fi

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
java -Dvertx.cacheDirBase=/tmp/vertx-cache -Dlogback.configurationFile=/app/logback.xml -jar collector-fat.jar -conf "{\"jwt.private\":\"$JWT_PRIVATE_KEY_FILE_PATH\",\"jwt.public\":\"$JWT_PUBLIC_KEY_FILE_PATH\",\"metrics.enabled\":$METRICS_ENABLED,\"mongo.userdb\":{\"db_name\":\"cyface-user\",\"connection_string\":\"mongodb://mongo-user:27017\",\"data_source_name\":\"cyface-user\"},\"mongo.datadb\":{\"db_name\":\"cyface-data\",\"connection_string\":\"mongodb://mongo-data:27017\",\"data_source_name\":\"cyface-data\"},\"admin.user\":\"$ADMIN_USER\",\"admin.password\":\"$ADMIN_PASSWORD\",\"http.port\":$CYFACE_API_PORT,\"http.host\":\"$CYFACE_API_HOST\",\"http.endpoint\":\"$CYFACE_API_ENDPOINT\",\"http.port.management\":$CYFACE_MANAGEMENT_PORT}" &> /logs/collector-out.log
