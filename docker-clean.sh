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
docker stop collector_api collector_prometheus collector_mongo-user collector_mongo-data collector_grafana

printf "\nDeleting containers\n"
docker rm collector_api
docker rm collector_prometheus
docker rm collector_mongo-user
docker rm collector_mongo-data
docker rm collector_grafana

printf "\nDeleting custom images\n"
docker rmi collector_api
docker rmi collector_prometheus
docker rmi collector_grafana

printf "\nRemoving data from volumes\n"
rm -r ./grafana/data/*
rm -r ./prometheus/data/*
rm -r ./data/*
rm -r ./file-uploads/*
rm -r ./user-data/*
