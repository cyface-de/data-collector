# Copyright 2018-2021 Cyface GmbH
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

# Use the official JDK Repo for version 11 as target (use slim version to remove all UI stuff, and since we do only need to run software, we just use the JRE).
FROM openjdk:11-jre-slim
MAINTAINER Cyface GmbH <mail@cyface.de>

# Set the working directory to /app
WORKDIR /app

# Used in dev environment, version-less
COPY collector-all.jar /app
COPY entrypoint.sh /app
COPY logback.xml /app
RUN chmod +x /app/entrypoint.sh

# Make port 8080 available to the world outside the container
EXPOSE 8080

# Install tooling required to check for running Mongo Database in entrypoint.sh
RUN apt-get update && apt-get install -y \
        netcat-openbsd

# Run the collector when the container launches
CMD ["./entrypoint.sh"]
