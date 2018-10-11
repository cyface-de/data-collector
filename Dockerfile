# Copyright 2018 Cyface GmbH
# 
# This file is part of the Cyface Data Collector.
# 
# The Cyface Data Collector is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#  
# The Cyface Data Collector is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.

# Use the official JDK Repo for version 8 as target (use slim version to remove all UI stuff, and since we do only need to run software, we just use the JRE).
FROM openjdk:8-jre-slim
MAINTAINER Cyface GmbH <mail@cyface.de>

# Set the working directory to /app
WORKDIR /app

COPY build/libs/collector-2.0.0-SNAPSHOT-fat.jar /app
COPY entrypoint.sh /app
RUN chmod +x /app/entrypoint.sh

# Make port 8080 available to the world outside the container
EXPOSE 8080

# Run the collector when the container launches
CMD ["./entrypoint.sh"]
