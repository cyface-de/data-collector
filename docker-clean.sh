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

export CURRENT_UID=$(id -u):$(id -g)

echo "Stopping running containers"
docker-compose stop

echo "Deleting containers"
docker rm collector_api_1
docker rm collector_prometheus_1
docker rm collector_mongo-user_1
docker rm collector_mongo-data_1
docker rm collector_grafana_1

echo "Deleting custom images"
docker rmi collector_api
docker rmi collector_prometheus
docker rmi collector_grafana

echo "Removing data from volumes"
rm -r ./grafana/data/*
rm -r ./prometheus/data/*
rm -r ./data/*
rm -r ./file-uploads/*
rm -r ./user-data/*
