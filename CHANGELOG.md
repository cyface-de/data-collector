# Changelog

This is the changelog for the Cyface data collector.
It lists the changes between versions and highlights compatibility issues with the Cyface SDK for iOS and Android.
We follow the [Semantic Versioning](http://semver.org) scheme and the guidelines from ["Keep a Changelog"](https://keepachangelog.com) as close as possible.

Since we did not run this changelog from the beginning early versions are not listed here.
Those version have never been published officially and thus changes to them are of no interest.

## [Unreleased] 

## [5.1.3] - 2020-07-10
### Fixed
* Enable Proper Logback-Logging

## [5.1.2] - 2020-01-23
### Info
* Update Github Actions workflow for Docker publication

## [5.1.1] - 2020-01-22
### Added
* Value for encryption salt is now configurable
### Fixed
* Management API documentation now shows the correct version
* Code style was changed to match requirements from PMD, Spotbugs and Checkstyle
* EventBus JUnit test now reports errors correctly
### Changed
* CI from Shippable to Github


## [5.1.0] - 2019-12-16
### Added
* Save file extension from uploaded files as 'fileType' to the meta data to distinguish upload files
* Support for the role parameter in UserCreation
* Add shared 'database' docker network for communication between backend services

## [5.0.0] - 2019-10-09
### Info
* Compatible with [Cyface Android SDK](https://github.com/cyface-de/android-backend) 5.0.0-beta1
* Compatible with [iOS SDK](https://github.com/cyface-de/ios-backend) 5.0.0-beta1
### Added
* Parameter to de-/activate metrics
* Accept events binary with upload (as added in the Cyface Android SDK V5)
### Changed
* Move default admin credentials to docker-compose.yml
* Rerouted log output to files in logs folder
* Only accept uploads with exactly two binary files (see Cyface Android SDK V5)
### Removed
* Integrated Grafana. It moved to a [separate project](https://github.com/cyface-de/grafana).

## [4.0.0] - 2019-08-06
### Info
* Initial release
* Compatible with [Cyface Android SDK](https://github.com/cyface-de/android-backend) 4.2.1
* Compatible with [iOS SDK](https://github.com/cyface-de/ios-backend) 4.6.1
