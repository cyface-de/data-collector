#!/bin/bash -e
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
#
# version 1.0.0

export CURRENT_UID=$(id -u):$(id -g)

# Create volume directories
mkdir -p data user-data secrets prometheus/data grafana/data grafana/logs

# Build jar
./gradlew clean assemble

# Build docker container
docker-compose build
