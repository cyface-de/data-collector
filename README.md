# Module collector

![https://vertx.io](https://img.shields.io/badge/vert.x-4.3.6-purple.svg)
![https://mongodb.com/](https://img.shields.io/badge/mongo-5.0.16-purple.svg)
![https://github.com/cyface-de/data-collector/actions](https://github.com/cyface-de/data-collector/actions/workflows/gradle_build.yml/badge.svg)

This application represents the [Cyface](https://cyface.de) data collector software.

It is used to collect traffic data from Cyface measurement devices, such as our sensor box or our smartphone application.

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
It starts with an explanation on how to set up all the required certificates.
This is a necessary prerequisite for all the following steps.
So **DO NOT** skip it.

Thereafter follows an explanation on how to run the Data Collector using either Docker or an IDE like IntelliJ or Eclipse.

### Certificates
The Cyface Data Collector authentication mechanism uses JSON Web Tokens (JWT). 
This mechanism requires asynchronous keys.

Keys you may use for testing and during development are provided.
Those keys are located in `src/test/resources`.
To use them outside of Unit tests you need to copy them to an appropriate location.
**ATTENTION: DO NOT USE THOSE KEYS IN A PRODUCTION ENVIRONMENT.**
Since they are in our repository, they are openly available to everyone on the internet, so everyone can compromise security on your server if you use the default keys.

The Cyface Data Collector requires two keys to issue and authenticate JWT tokens for users trying to communicate with the service.
Just place the appropriate files as `private_key.pem` and `public.pem` in `secrets/jwt`, right next to the `docker-compose.yml` file, or in the "working directory" selected in your run configuration in your IDE (e.g. `data-collector/`).

To generate new keys follow the instructions in the [Vert.x documentation](https://vertx.io/docs/vertx-auth-jwt/java/#_loading_keys) for *Using RSA keys*.

### Building

To build the docker container running the API simply execute `./gradlew :clean :build :copyToDockerBuildFolder`.
This builds the jar file which is then packed into the Docker container which is build afterwards.
Please refer to the previous section about **Certificates** prior to building.

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

This calls docker to bring up a Mongo-database container and a container running the Cyface data collector API. The Collector API is by default available via port 8080. This means if you boot up everything using the default settings, the Collector API is accessible via `http://localhost:8080/api/v4/`.

**ATTENTION: The docker setup should only be used for development purposes.**
It exposes the Cyface data collector as well as the ports of both Mongo database instances freely on the local network.

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

* **jwt.private:** The path of the file containing the private key used to sign JWT keys.
* **jwt.public:** The path of the file containing the public key used to sign JWT keys.
* **http.port:** The port the API  is available at.
* **http.host:** The hostname under which the Cyface Data Collector is running. This can be something like `localhost`.
* **http.endpoint:** The path to the endpoint the Cyface Data Collector. This defaults to `/api/v4`.
* **mongo.db:** Settings for a Mongo database storing information about all the users capable of logging into the system and all data uploaded via the Cyface data collector. This defaults to a Mongo database available at `mongodb://127.0.0.1:27017`. The value of this should be a JSON object configured as described [here](https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client).
* **admin.user:** The username of a default administration account which is created if it does not exist upon start up.
* **admin.password:** The password for the default administration account.
* **salt.path:** The path to a salt file used to encrypt passwords stored in the user database even stronger.
* **salt:** A salt value that may be used instead of the salt from salt.path. You must make sure that either the salt or the salt.path parameter are used. If both are specified the application startup will fail.
* **metrics.enabled:** Set to either `true` or `false`. If `true` the collector API publishes metrics using micrometer. These metrics are accessible by a [Prometheus](https://prometheus.io/) server (Which you need to set up yourself) at port `8081`.
* **http.port.management:** The port running the management API responsible for creating user accounts.
* **jwt.expiration**: The time it takes for a JWT token to expire in seconds. If a JWT token expires, clients need to acquire a new one via username and password authentication. Setting this time too short requires sending the username and password more often. This makes it easier for malicious parties to intercept and brute force usernames and passwords. However long time JWT tokens may be captured as well and used for malicious purposes.
* **upload.expiration:** The time an interrupted upload is stored for continueation in the future in milliseconds. If this time expires, the upload must start from the beginning.
* **measurement.payload.limit:** The size of a measurement in bytes up to which it is accepted as a single upload. Larger measurements are transmitted in chunks.
* **storage-type:** The type of storage to use for the uploaded data. Currently either `gridfs` or `google` is supported. The following parameter are required:
  * **gridfs**
    * **type:** Must be `gridfs` in this case.
    * **uploads-folder:** The relative or absolute path to a folder, to store temporary not finished uploads on the local harddrive before upload of the complete data blob to GridFS upon completion.
  * **google**
    * **type:** Must be `google` in this case.
    * **collection-name:** The name of a Mongo collection to store an uploads metadata.
    * **project-identifier:** A Google Cloud Storage project identifier to where the upload bucket is located.
    * **bucket-name:** The Google Cloud Storage bucket name to load the data into.
    * **credentials-file:** A credentials file used to authenticate with the Google Cloud Storage account used to upload the data to the Cloud.
    * **paging-size:** The number of buckets to load per request, when iterating through all the data uploaded. Large numbers require fewer requests but more memory.

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

# Package de.cyface.collector.model

Contains all the data model files required by the Cyface Data Collector.

# Package de.cyface.collector.storage

Contains the interface to store data in Cyface and several implementations for that interface.

Those implementations provide support for storing data in GridFS, on the local file system and in Google Cloud storage.

The following image shows an overview of the interface and how it is embedded in the Cyface data collector.

<img src="../../images/storage-service.png" alt="Test" width="1128px" height="292px">
