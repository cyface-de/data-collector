# Module collector

![https://vertx.io](https://img.shields.io/badge/vert.x-4.3.6-purple.svg)
![https://mongodb.com/](https://img.shields.io/badge/mongo-5.0.16-purple.svg)
![https://github.com/cyface-de/data-collector/actions](https://github.com/cyface-de/data-collector/actions/workflows/gradle_build.yml/badge.svg)

This application represents the [Cyface](https://cyface.de) data collector software.

It is used to collect traffic data from Cyface measurement devices, such as our smartphone application.

Our smartphone SDK is available as GPL application for [Android](https://github.com/cyface-de/android-backend) and [iOS](https://github.com/cyface-de/ios-backend) (or as [Podspec](https://github.com/cyface-de/ios-podspecs)) as well.

If you require this software under a closed source license for you own projects, please [contact us](https://www.cyface.de/#kontakt).

Changes between versions are found in the [Release Section](https://github.com/cyface-de/data-collector/releases).

The project uses [Gradle](https://gradle.org/) as the build system.

## Overview

* [Collector](#collector)

.General information
* [Release a new Version](#release-a-new-version)
* [Publishing Artifacts to GitHub Packages manually](#publishing-artifacts-to-github-packages-manually)
* [To Do](#to-do)
* [Licensing](#licensing)

## Collector

A program which provides the ability to collect data, as e.g. sent by the Cyface SDKs.

The following sections explain how to run the Data Collector
It starts with an explanation on how to set up all the required steps.
This is a necessary prerequisite for all the following steps.
So **DO NOT** skip it.

Thereafter, follows an explanation on how to run the Data Collector using either Docker or an IDE like IntelliJ or Eclipse.

### Building

To build the docker container running the API simply execute `./gradlew :clean :build :copyToDockerBuildFolder`.
This builds the jar file which is then packed into the Docker container which is build afterwards.

When you updated the Swagger UI make sure to clear your browser cache or else it might not update.

### Execution
This section describes how to execute the Cyface Data Collector.

It begins with an explanation on how to run the Cyface Data Collector from a Docker environment.
This is the recommended variant if you do not need to change the collector itself, but only need to develop against its API.

The section continues with an explanation on the supported configuration parameters.
If you are not using the Docker environment, you will probably have to set a few of them to the correct values, before running the Cyface Data Collector.

The last two sections provide explanations on how to run the software directly from the terminal or from within an IDE such as Eclipse or IntelliJ.
For these execution variants you need the parameters explained in the preceding section.

#### Running from Docker

Configure logback or use the sample configuration: `cp src/main/docker/logback.xml.template src/main/docker/logback.xml`

The app is executed by a non-privileged user inside the Docker container. To allow this user to
write data to `logs` and `file-uploads` you need to create two folders and then set the permissions for both folders to `chmod o+w`, see [DAT-797]:
`mkdir src/main/docker/logs src/main/docker/file-uploads && sudo chmod  o+w src/main/docker/file-uploads src/main/docker/logs`

Now build the system as described in the "Building" section above:
`./gradlew :clean :build :copyToDockerBuildFolder`

Then simply run `docker-compose up` inside `build/docker`:
`cd build/docker/ && docker-compose up -d`

This calls docker to bring up a Mongo-database container and a container running the Cyface data collector API. 
The Collector API is by default available via port 8080. 
This means if you boot up everything using the default settings, the Collector API is accessible via `http://localhost:8080/api/v4/`.

**ATTENTION: The docker setup should only be used for development purposes.**
It exposes the Cyface data collector as well as the ports of the Mongo database instance freely on the local network.

Use `docker-compose ps` to see which ports are mapped to which by Docker.
For using such a setup in production, you may create your own Docker setup, based on our development one.

#### Running without Docker
Running the Cyface Data Collector without Docker, like for example from the terminal or from within your IDE is a little more complex.
It requires a few set up steps and command knowledge as explained in the following paragraphs.

##### Running a Mongo Database for Data and User Storage
Before you can run the Cyface data collector you need to set up a Mongo database.

If you use the Docker environment as explained above, this is done for you.
If you run the Cyface Data Collector on your own, you are responsible for providing a valid environment, including Mongo.

The database is used to store the collected data and information about valid user accounts.
For information on how to install and run a Mongo database on your machine please follow the [tutorial](https://docs.mongodb.com/manual/installation/#mongodb-community-edition).
If you take the default installation, the default settings of the Cyface data collector should be sufficient to connect to that instance.
**ATTENTION: However be aware this is not recommended as a production environment.**

##### Running a Google Cloud Store for Data
As an alternative for storing data to a Mongo GridFS database, the Cyface Data 
Collector provides the possibility to use Google Cloud Storage for storing received data.

You may configure this as explained in the section about valid arguments.
Notice however that a Mongo database is still required to store user data for authentication and authorization as explained above.

#### Data Collector Arguments
The Cyface data collector requires a few parameters to fine tune the runtime.
The parameters are provided using the typical [Vertx `-conf` parameter](https://vertx.io/docs/vertx-core/java/#_the_vertx_command_line) with a value in JSON notation.

The following parameters are supported:

* **http.port:** The port the API  is available at.
* **http.host:** The hostname under which the Cyface Data Collector is running. This can be something like `localhost`.
* **http.endpoint:** The path to the endpoint the Cyface Data Collector. This defaults to `/api/v4`.
* **mongo.db:** Settings for a Mongo database storing information about all the users capable of logging into the system and all data uploaded via the Cyface data collector. This defaults to a Mongo database available at `mongodb://127.0.0.1:27017`. The value of this should be a JSON object configured as described [here](https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client).
* **metrics.enabled:** Set to either `true` or `false`. If `true` the collector API publishes metrics using micrometer. These metrics are accessible by a [Prometheus](https://prometheus.io/) server (Which you need to set up yourself) at port `8081`.
* **upload.expiration:** The time an interrupted upload is stored for continuation in the future in milliseconds. If this time expires, the upload must start from the beginning.
* **measurement.payload.limit:** The size of a measurement in bytes up to which it is accepted as a single upload. Larger measurements are transmitted in chunks.
* **storage-type:** The type of storage to use for the uploaded data. Currently, either `gridfs` or `google` is supported. The following parameter are required:
  * **gridfs**
    * **type:** Must be `gridfs` in this case.
    * **uploads-folder:** The relative or absolute path to a folder, to store temporary not finished uploads on the local hard drive before upload of the complete data blob to GridFS upon completion.
  * **google**
    * **type:** Must be `google` in this case.
    * **collection-name:** The name of a Mongo collection to store uploads' metadata into.
    * **project-identifier:** A Google Cloud Storage project identifier to where the upload bucket is located.
    * **bucket-name:** The Google Cloud Storage bucket name to load the data into.
    * **credentials-file:** A credentials file used to authenticate with the Google Cloud Storage account used to upload the data to the Cloud.
    * **paging-size:** The number of buckets to load per request, when iterating through all the data uploaded. Large numbers require fewer requests but more memory.
* **auth-type:** The type of authentication service to use. Currently, either `mocked` or `oauth` is supported. Defaults to `oauth`. Both require the following parameters:
  * **oauth.callback**: The callback URL you entered in your provider admin console. This defaults to `http://localhost:8080/callback`.
  * **oauth.client**: The name of the oauth client to contact. This defaults to `collector`.
  * **oauth.secret**: The secret of the oauth client to contact.
  * **oauth.site**: The Root URL for the provider without trailing slashes. This defaults to `https://auth.cyface.de:8443/realms/{tenant}`.
  * **oauth.tenant**: The name of the oauth realm to contact. This defaults to `rfr`.

#### Running from Command Line

To launch your tests:

```
./gradlew clean test
```

To package your application:

```
./gradlew clean assemble
```

To run your application with the settings from `conf.json`:

```
./gradlew run --args="run de.cyface.collector.verticle.MainVerticle -conf conf.json"
```

#### Running from IDE
To run directly from within your IDE you need to use the `de.cyface.collector.Application` class, which is a subclass of the [Vert.x launcher](https://vertx.io/docs/vertx-core/java/#_the_vert_x_launcher). Just specify it as the main class in your launch configuration with the program argument `run de.cyface.collector.verticle.MainVerticle`.

### Mongo Database

#### Setup
The following is not strictly necessary but advised if you run in production or if you encounter strange problems related to data persistence.
Consider reading the [Mongo Database Administration Guide](https://docs.mongodb.com/manual/administration/) and follow the advice mentioned there.

#### Administration
To load files from the Mongo GridFS file storage use the [Mongofiles](https://docs.mongodb.com/manual/reference/program/mongofiles/) tool.

* Showing files: `mongofiles --port 27019 -d cyface list`
* Downloading files: `mongofiles --port 27019 -d cyface get f5823cbc-b8f5-4c80-a4b1-7bf28a3c7944`
* Unzipping files: `printf "\x78\x9c" | cat - f5823cbc-b8f5-4c80-a4b1-7bf28a3c7944 | zlib-flate -uncompress > test2`

## Using the Cyface Data Collector

To provide data to the Cyface Data Collector, call the appropriate endpoints of the Data Collector REST API.

The available endpoints are documented as OpenAPI.
The OpenAPI documentation is available under 'http://localhost:8080/api/v4/` if the collector runs under `localhost`.

The protocol for uploading data follows the [Google data protocol](https://developers.google.com/gdata/docs/resumable_upload).

### Accessing the Docker Development Environment
In the Docker development environment, all containers communicate through the internal Docker networks.

Tokens issued to a client outside this network won't be valid within the network due to a mismatch in the issuer encoded into the token.
For instance, it might use "localhost:8081" instead of the expected "authentication:8080" when requesting a token from outside.
The `rfr` realm, which is initialized in this setup from `./src/main/docker/keycloak/data/import/rfr-realm.json`, addresses
this problem by setting the "frontendUrl" to "http://authentication:8080". If you add more realms, ensure you do the same. 

To obtain an authentication token from outside the Docker network, you can use the following command:
```
$ curl -d 'client_id=ios-app' -d 'username=test@cyface.de' -d 'password=test' -d 'grant_type=password' 'http://localhost:8081/realms/rfr/protocol/openid-connect/token'
```

The output will look similar to this:
```
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0{"access_token":"eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJDSnNhZ0ZnbGNGRnpDWTU1Z3ZYb2xhMnRMWFlDMzNDQmtUU05tMU1DbkdnIn0.eyJleHAiOjE2OTMzOTc2MTcsImlhdCI6MTY5MzM5NzMxNywianRpIjoiYzQ3ZTUzYzktOTA0ZC00MDIyLWE5MWEtZTcwOGNlZGQ0NWUxIiwiaXNzIjoiaHR0cDovL2F1dGhlbnRpY2F0aW9uOjgwODAvcmVhbG1zL3JmciIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiIyYzAyZjI4ZS0wMGI4LTQyMWEtOTIxZi1iZTY3MGI5ZDkzMzAiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJpb3MtYXBwIiwic2Vzc2lvbl9zdGF0ZSI6ImE1ZmU2YTJjLWE0MDItNGFlMy1hZDUxLWMzNTlkOTExZTdkYSIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib2ZmbGluZV9hY2Nlc3MiLCJkZWZhdWx0LXJvbGVzLXJmciIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIGVtYWlsIiwic2lkIjoiYTVmZTZhMmMtYTQwMi00YWUzLWFkNTEtYzM1OWQ5MTFlN2RhIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJUZXN0IFRlc3RlciIsInByZWZlcnJlZF91c2VybmFtZSI6InRlc3RAY3lmYWNlLmRlIiwiZ2l2ZW5fbmFtZSI6IlRlc3QiLCJmYW1pbHlfbmFtZSI6IlRlc3RlciIsImVtYWlsIjoidGVzdEBjeWZhY2UuZGUifQ.jhppkwSJCwYlbje_jU-7SQXJPrUYyjiXp29xoh2Vg6lga_hWtPOttF95D96z6IbNPFc42GyjRq_PFRqtt5M2K0yPYL36HeAHLpIYCV6SJEA4do10P90o07tFKkJReWGEgswSsA6x-79Y3sVjhADEJCJozXWZ-Wb7tUtWdCvqUZecmLaVquDPcRdDXfo98K4qQe2p65_QPR2lMzz86EIp2a3-xUwLWmc62_mZfZ1wUtvHFty1IXLZC_L1pD9p99DdLy7Rb8UKpMBH0a646jBs9CPOY481_sOMOSnbyrughRrlJiV7oRl7rZxZhP0djMRj29nB8L_Z5NWc21XJcWp9VA","expires_in":300,"refresh_expires_in":86313600,"refresh_token":"eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJlMDEwYzQ2NS04YTRjLTQ3NzItODY2MS01NThkNWZhNGZmYTEifQ.eyJleHAiOjE3Nzk3MTA5MTcsImlhdCI6MTY5MzM5NzMxNywianRpIjoiMGY0NTBkMzQtMjk3Zi00NjJiLWJmY2MtMTFlNmM2MWUxMGI3IiwiaXNzIjoiaHR0cDovL2F1dGhlbnRpY2F0aW9uOjgwODAvcmVhbG1zL3JmciIsImF1ZCI6Imh0dHA6Ly9hdXRoZW50aWNhdGlvbjo4MDgwL3JlYWxtcy9yZnIiLCJzdWIiOiIyYzAyZjI4ZS0wMGI4LTQyMWEtOTIxZi1iZTY3MGI5ZDkzMzAiLCJ0eXAiOiJSZWZyZXNoIiwiYXpwIjoiaW9zLWFwcCIsInNlc3Npb25fc3RhdGUiOiJhNWZlNmEyYy1hNDAyLTRhZTMtYWQ1MS1jMzU5ZDkxMWU3ZGEiLCJzY29wZSI6InByb2ZpbGUgZW1haWwiLCJzaWQiOiJhNWZlNmEyYy1hNDAyLTRhZTMtYWQ1MS1jMzU5ZDkxMWU3ZGEifQ.Bp7f5ycWPC0bgHx4_gBBMOM1uKJLqLD3100  2293  100  2218  100    75   8837    298 --:--:-- --:--:-- --:--:--  91356a2c-a402-4ae3-ad51-c359d911e7da","scope":"profile email"}
```
From this it is possible to copy the access token and start the upload process.

## Release a new Version

To release a new version:

* `version`s in root `build.gradle` and `src/main/resources/webroot/*/openapi.yml` are automatically set by the CI
* Just tag the new release version on the `main` branch. Follow the guidelines from ["Keep a Changelog"](https://keepachangelog.com) in your tag description
* Push the release tag to GitHub. The artifacts are automatically published when a new version is tagged and pushed by our
  [GitHub Actions](https://github.com/cyface-de/data-collector/actions) to the
  [GitHub Registry](https://github.com/cyface-de/data-collector/packages).
* The CI workflow automatically marks the release on Github


## Publishing artifacts to GitHub Packages manually

The artifacts produced by this project are distributed via [GitHubPackages](https://github.com/features/packages).
Before you can publish artifacts you need to rename `gradle.properties.template` to `gradle.properties` and enter your GitHub credentials.
How to obtain these credentials is described [here](https://help.github.com/en/github/managing-packages-with-github-packages/about-github-packages#about-tokens).

To publish a new version of an artifact you need to:

1. Increase the version number of the subproject within the `build.gradle` file
2. Call `./gradlew publish`

This will upload a new artifact to GitHub packages with the new version.
GitHub Packages will not accept to overwrite an existing version or to upload a lower version.
This project uses [semantic versioning](https://semver.org/).

## To Do
* Setup Cluster
	* Vertx
	* MongoDb

## Licensing
Copyright 2018-2023 Cyface GmbH

This file is part of the Cyface Data Collector.

The Cyface Data Collector is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

The Cyface Data Collector is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with the Cyface Data Collector.  If not, see http://www.gnu.org/licenses/.

# Package de.cyface.collector.handler

This package contains the Vert.x handler classes used to handle various
* events during the runtime of the application. Usually those handlers react to
* events triggered by clients calling API endpoints.

# Package de.cyface.collector.model

Contains all the data model files required by the Cyface Data Collector.

# Package de.cyface.collector.storage

Contains the interface to store data in Cyface and several implementations for that interface.

Those implementations provide support for storing data in GridFS, on the local file system and in Google Cloud storage.

The following image shows an overview of the interface and how it is embedded in the Cyface data collector.

<img src="images/storage-service.png" alt="Test" width="1128px" height="292px">

# Package de.cyface.collector

This package contains the top level classes used by the Cyface Data Collector. Most important is the class
* [de.cyface.collector.Application], which starts the whole application and the
* [de.cyface.collector.verticle.CollectorApiVerticle] that initializes the server.
