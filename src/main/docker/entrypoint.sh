#!/bin/bash
# Copyright 2018-2023 Cyface GmbH
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

# Entrypoint script for the Docker container which starts the Collector API.
#
# author: Klemens Muthmann
# author: Armin Schnabel
# Version 1.0.0

DEFAULT_API_PORT="8080"
JAR_FILE="collector-all.jar"
LOG_FILE="/app/logs/collector-out.log"
SERVICE_NAME="Cyface Collector API"

main() {
  loadAuthParameters
  loadApiParameters
  loadCollectorParameters
  loadConfig
  waitForDatabase "mongo"
  startApi
}

loadApiParameters() {
  if [ -z "$CYFACE_API_PORT" ]; then
    CYFACE_API_PORT=$DEFAULT_API_PORT
  fi

  if [ -z "$CYFACE_API_HOST" ]; then
    CYFACE_API_HOST="localhost"
  fi

  if [ -z $CYFACE_API_ENDPOINT ]; then
    CYFACE_API_ENDPOINT="/api/v4/"
  fi
}

loadAuthParameters() {
  if [ -z "$CYFACE_AUTH_TYPE" ]; then
    CYFACE_AUTH_TYPE="oauth"
  fi
  if [ -z "$CYFACE_OAUTH_CALLBACK" ]; then
    CYFACE_OAUTH_CALLBACK="http://localhost:8080/callback"
  fi
  if [ -z "$CYFACE_OAUTH_CLIENT" ]; then
    CYFACE_OAUTH_CLIENT="collector"
  fi

  if [ -z CYFACE_OAUTH_SECRET ]; then
      echo "Unable to find OAuth client secret. Please set the environment variable CYFACE_OAUTH_SECRET to an appropriate value! API will not start!"
      exit 1
  fi

  if [ -z "$CYFACE_OAUTH_SITE" ]; then
    CYFACE_OAUTH_SITE="https://auth.cyface.de:8443/realms/{tenant}"
  fi
  if [ -z "$CYFACE_OAUTH_TENANT" ]; then
    CYFACE_OAUTH_TENANT="rfr"
  fi

  echo "Using Auth type: $CYFACE_AUTH_TYPE"
  echo "Using OAuth callback $CYFACE_OAUTH_CALLBACK"
  echo "Using OAuth client $CYFACE_OAUTH_CLIENT"
  echo "Using OAuth site $CYFACE_OAUTH_SITE"
  echo "Using OAuth tenant $CYFACE_OAUTH_TENANT"
}

loadCollectorParameters() {
  # Monitoring
  if [ -z $METRICS_ENABLED ]; then
    METRICS_ENABLED="false"
  fi

  echo "Enabling metrics reporting by API: $METRICS_ENABLED."
}

# Injects the database parameters for databases
loadConfig() {
  CONFIG="{\
      \"mongo.db\":{\
          \"db_name\":\"cyface\",\
          \"connection_string\":\"mongodb://mongo:27017\",\
          \"data_source_name\":\"cyface\"\
      },\
      \"http.port\":$CYFACE_API_PORT,\
      \"http.host\":\"$CYFACE_API_HOST\",\
      \"http.endpoint\":\"$CYFACE_API_ENDPOINT\",\
      \"metrics.enabled\":$METRICS_ENABLED,\
      \"upload.expiration\":60000,\
      \"measurement.payload.limit\":104857600,\
      \"storage-type\":{\
          \"type\":\"gridfs\",\
	        \"uploads-folder\":\"file-uploads\"\
      },\
      \"auth-type\":\"$CYFACE_AUTH_TYPE\",
      \"oauth.callback\":\"$CYFACE_OAUTH_CALLBACK\",\
      \"oauth.client\":\"$CYFACE_OAUTH_CLIENT\",\
      \"oauth.secret\":\"$CYFACE_OAUTH_SECRET\",\
      \"oauth.site\":\"$CYFACE_OAUTH_SITE\",\
      \"oauth.tenant\":\"$CYFACE_OAUTH_TENANT\"\
  }"
}

# Parameter 1: Name of the Docker Container which contains the Mongo Database to wait for
waitForDatabase() {
  echo "Waiting for Database $1 to start!"

  MONGO_STATUS="not running"
  COUNTER=0
  while [ "$COUNTER" -lt 10 ] && [ "$MONGO_STATUS" = "not running" ]; do
    ((COUNTER++))
    echo "Try $COUNTER"
    if nc -z "$1" 27017; then
      echo "Mongo Database is up!"
      MONGO_STATUS="running"
    else
      sleep 5s
    fi
  done
  if [ "$COUNTER" -ge 10 ]; then
      echo "Unable to find $1 Database! API will not start."
      exit 1
  fi
}

startApi() {
  echo "Starting $SERVICE_NAME at $CYFACE_API_HOST:$CYFACE_API_PORT$CYFACE_API_ENDPOINT"
  java -Dvertx.cacheDirBase=/tmp/vertx-cache \
      -Dlogback.configurationFile=/app/logback.xml \
      -jar $JAR_FILE \
      -conf "$CONFIG" \
      &> $LOG_FILE
}

main "$@" # $@ allows u to access the command-line arguments withing the main function
