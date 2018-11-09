#!/bin/bash
# Copyright 2018 Cyface GmbH
# version 1.0.0
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

printf "\nStopping running containers\n"
docker-compose stop

printf "\nDeleting containers\n"
docker rm data-collector_api_1
docker rm data-collector_prometheus_1
docker rm data-collector_mongo-user_1
docker rm data-collector_mongo-data_1
docker rm data-collector_grafana_1

printf "\nDeleting custom images\n"
docker rmi data-collector_api
docker rmi data-collector_prometheus
docker rmi data-collector_grafana

printf "\nRemoving data from volumes\n"
rm -r ./grafana/data/*
rm -r ./prometheus/data/*
rm -r ./data/*
rm -r ./file-uploads/*
rm -r ./user-data/*
