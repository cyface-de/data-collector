# Changelog

This is the changelog for the Cyface data collector.
It lists the changes between versions and highlights compatibility issues with the Cyface SDK for iOS and Android.
We follow the [Semantic Versioning](http://semver.org) scheme and the guidelines from ["Keep a Changelog"](https://keepachangelog.com) as close as possible.

Since we did not run this changelog from the beginning early versions are not listed here.
Those version have never been published officially and thus changes to them are of no interest.

## [Unreleased] 

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
