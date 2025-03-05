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
FROM eclipse-temurin:17-jre
MAINTAINER Cyface GmbH <mail@cyface.de>

# Create user. Unproblematic default id (wikipedia.org/wiki/User_identifier)
ARG USER_ID=9999
ARG GROUP_ID=9999
# For some reasons `addgroup` and `adduser` are not available anymore in the base image.
RUN groupadd --gid $GROUP_ID user
RUN useradd -c '' --uid $USER_ID --gid $GROUP_ID --no-create-home --shell /usr/sbin/nologin user
# The `useradd` version does not support `--disabled-password`, so we lock the user password instead:
RUN passwd -l user

# Set the working directory to /app
WORKDIR /app

# Used in dev environment, version-less
COPY collector-all.jar /app
COPY entrypoint.sh /app
RUN chmod +x /app/entrypoint.sh

# Adjust permissions
# Cannot create logs folder with the correct permissions here as the permissions will be overwritten [DAT-797]
RUN chown user:user entrypoint.sh collector-all.jar

# Make port 8080 available to the world outside the container
EXPOSE 8080

# Install tooling required to check for running Mongo Database in entrypoint.sh
RUN apt update && apt install -y \
        netcat-openbsd

# Run app by non-privileged user.
USER user

# Run the collector when the container launches
CMD ["./entrypoint.sh"]
