# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Run all tests
./gradlew clean test

# Run a single test class
./gradlew test --tests de.cyface.collector.SomeTestClass

# Lint (Detekt static analysis)
./gradlew detekt

# Build fat JAR and prepare Docker artifacts
./gradlew :clean :build :copyToDockerBuildFolder

# Run the application locally (requires conf.json)
./gradlew run --args="run de.cyface.collector.verticle.MainVerticle -conf conf.json"

# Publish to GitHub Packages (requires gradle.properties with gpr.user / gpr.key)
./gradlew publish
```

## Local Development Setup

1. Copy templates:
   ```bash
   cp conf.json.template conf.json
   cp src/main/resources/logback.xml.template src/main/resources/logback.xml
   cp gradle.properties.template gradle.properties  # then add GitHub credentials
   ```

2. For Docker-based dev (JWT auth):
   ```bash
   export CYFACE_JWK='{"kty":"RSA","alg":"RS256","use":"sig","kid":"1","n":"...","e":"AQAB"}'
   mkdir src/main/docker/container-jwt/logs src/main/docker/container-jwt/file-uploads
   sudo chmod o+w src/main/docker/container-jwt/file-uploads src/main/docker/container-jwt/logs
   ./gradlew :clean :build :copyToDockerBuildFolder
   cd build/docker && docker compose -f compose-jwt.yaml up -d --build --force-recreate
   ```

3. API is available at `http://localhost:8080/api/v4/` (OpenAPI docs at same path).

## Architecture

This is a **Vert.x 4.x / Kotlin reactive REST API** that receives traffic measurement data from Cyface mobile SDKs and stores it in MongoDB (GridFS) or Google Cloud Storage.

### Startup Flow

`Application.kt` (Vert.x launcher) → `MainVerticle` (deploys auth + storage) → `CollectorApiVerticle` (HTTP router + endpoints) → request `Handler` classes → `DataStorageService`

### Key Packages

| Package | Responsibility |
|---|---|
| `verticle/` | Vert.x verticles: `MainVerticle` (entry point), `CollectorApiVerticle` (API wiring), `HttpServer` (server setup) |
| `handler/` | HTTP request handlers for each endpoint; `upload/` sub-package has `PreRequestHandler`, `StatusHandler`, `UploadHandler` |
| `auth/` | Pluggable auth: `JWKAuthHandlerBuilder` (JWT), `OAuth2HandlerBuilder` (Keycloak), `MockedHandlerBuilder` (tests) |
| `storage/` | `DataStorageService` interface + implementations: `gridfs/` (MongoDB GridFS, default), `cloud/` (Google Cloud Storage), `local/` (placeholder) |
| `model/` | Domain model: `Measurement`, `Attachment`, `Upload`, `ContentRange`, `FormAttributes`, and `metadata/` subtypes |
| `configuration/` | `Configuration` class that maps `conf.json` to typed config; `StorageType` sealed hierarchy |

### Upload Protocol

Follows the [Google resumable upload protocol](https://developers.google.com/gdata/docs/resumable_upload). Uploads are multi-step: `PreRequestHandler` initiates and stores session state in MongoDB; `UploadHandler` receives chunks; `StatusHandler` reports progress. Stale uploads are cleaned up by `CleanupOperation` implementations.

### Authentication

Two modes, selected via `auth.type` in `conf.json`:
- **`jwt`**: Validates tokens against a JWK (set via `CYFACE_JWK` env var or `auth.jwk` config key)
- **`oauth`**: Delegates to a Keycloak/OIDC provider (`auth.site`, `auth.tenant`, `auth.client`, `auth.secret`)

In the Docker dev environment, tokens must be requested through the internal Docker network hostname (`authentication:8080`), not `localhost`. Use `client_id=ios-app`, realm `rfr`, credentials `test@cyface.de / test`.

### Storage Backends

Selected via `storage-type.type` in `conf.json`:
- **`gridfs`**: Stores data in MongoDB GridFS; requires `uploads-folder` for temporary incomplete uploads
- **`google`**: Streams to Google Cloud Storage; requires `project-identifier`, `bucket-name`, `credentials-file`
- **`local`**: Placeholder, not fully implemented

### Code Quality Rules (Detekt)

- Max line length: **120 characters**
- Max cyclomatic complexity: **15**
- Max function length: **60 lines** (excluding tests)
- Forbidden comments: `FIXME`, `TODO`, `STOPSHIP`
- Config: `config/detekt.yml`; baseline: `detekt-baseline.xml`