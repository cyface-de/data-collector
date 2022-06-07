/*
 * Copyright 2021-2022 Cyface GmbH
 *
 * This file is part of the Cyface Data Collector.
 *
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.handler;

import static de.cyface.collector.handler.PreRequestHandler.DEVICE_ID_FIELD;
import static de.cyface.collector.handler.PreRequestHandler.MEASUREMENT_ID_FIELD;
import static de.cyface.collector.handler.PreRequestHandler.MINIMUM_LOCATION_COUNT;
import static de.cyface.collector.handler.PreRequestHandler.PRECONDITION_FAILED;
import static de.cyface.collector.handler.PreRequestHandler.bodySize;
import static de.cyface.collector.handler.StatusHandler.RESUME_INCOMPLETE;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.api.Authorizer;
import de.cyface.api.PauseAndResumeBeforeBodyParsing;
import de.cyface.api.model.User;
import de.cyface.collector.handler.exception.IllegalSession;
import de.cyface.collector.handler.exception.InvalidMetaData;
import de.cyface.collector.handler.exception.PayloadTooLarge;
import de.cyface.collector.handler.exception.SessionExpired;
import de.cyface.collector.handler.exception.SkipUpload;
import de.cyface.collector.handler.exception.Unparsable;
import de.cyface.collector.model.Measurement;
import de.cyface.collector.verticle.Config;
import de.cyface.model.RequestMetaData;
import io.vertx.core.MultiMap;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.mongo.GridFsUploadOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

/**
 * A handler for receiving HTTP PUT requests on the "measurements" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
@SuppressWarnings("PMD.AvoidCatchingNPE")
public final class MeasurementHandler extends Authorizer {

    /**
     * The logger for objects of this class. You can change its configuration by
     * adapting the values in <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);
    /**
     * HTTP status code to return when the client tries to resume an upload but the session has expired.
     */
    static final int NOT_FOUND = 404;
    /**
     * The field name for the session entry which contains the path of the temp file containing the upload binary.
     * <p>
     * This field is set in the {@link MeasurementHandler} to support resumable upload.
     */
    static final String UPLOAD_PATH_FIELD = "upload-path";
    /**
     * The folder to cache file uploads until they are persisted.
     */
    public static final Path FILE_UPLOADS_FOLDER = Path.of("file-uploads/");
    /**
     * Vertx <code>MongoClient</code> used to access the database to write the received data to.
     */
    private final MongoClient mongoClient;
    /**
     * The maximum number of {@code Byte}s which may be uploaded.
     */
    private final long payloadLimit;

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param config the configuration setting required to start the HTTP server
     */
    public MeasurementHandler(final Config config) {
        this(config.getDatabase(), config.getAuthProvider(), config.getMeasurementLimit());
    }

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param mongoClient The client to use to access the Mongo database.
     * @param authProvider An auth provider used by this server to authenticate against the Mongo user database
     * @param payloadLimit The maximum number of {@code Byte}s which may be uploaded.
     */
    public MeasurementHandler(final MongoClient mongoClient, final MongoAuthentication authProvider,
            final long payloadLimit) {
        super(authProvider, mongoClient, new PauseAndResumeBeforeBodyParsing());
        Validate.notNull(mongoClient);
        Validate.isTrue(payloadLimit > 0);
        this.mongoClient = mongoClient;
        this.payloadLimit = payloadLimit;
    }

    @Override
    protected void handleAuthorizedRequest(final RoutingContext ctx, final Set<User> users,
            final MultiMap header) {
        LOGGER.info("Received new measurement request.");
        final var request = ctx.request();
        final var session = ctx.session();

        try {
            // Load authenticated user
            final var username = ctx.user().principal().getString("username");
            final var matched = users.stream().filter(u -> u.getName().equals(username)).collect(Collectors.toList());
            Validate.isTrue(matched.size() == 1);
            final var loggedInUser = matched.stream().findFirst().get(); // Make sure it's the matched user

            final var bodySize = bodySize(request.headers(), payloadLimit, "content-length");
            final var metaData = metaData(request);
            checkSessionValidity(session, metaData);

            // Handle upload status request
            if (bodySize == 0) {
                new StatusHandler(mongoClient).handle(ctx);
                return;
            }

            // Handle first chunk
            final var contentRange = contentRange(request, bodySize);
            if (session.get(UPLOAD_PATH_FIELD) == null) {
                handleFirstChunkUpload(ctx, request, session, loggedInUser, contentRange, metaData, null);
                return;
            }

            // Search for previous upload chunk
            final var fs = ctx.vertx().fileSystem();
            final var path = (String)session.get(UPLOAD_PATH_FIELD);
            request.pause();
            fs.exists(path).onSuccess(fileExists -> {
                try {
                    request.resume();

                    if (!fileExists) {
                        session.remove(UPLOAD_PATH_FIELD); // was linked to non-existing file
                        handleFirstChunkUpload(ctx, request, session, loggedInUser, contentRange, metaData, path);
                        return;
                    }

                    handleSubsequentChunkUpload(ctx, request, session, loggedInUser, contentRange, metaData, path);
                } catch (final RuntimeException e) {
                    ctx.fail(500, e);
                }
            }).onFailure(failure -> {
                LOGGER.error("Response: 500, failed to check if temp file exists");
                ctx.fail(500, failure);
            });
        } catch (InvalidMetaData | Unparsable | IllegalSession | PayloadTooLarge e) {
            LOGGER.error(String.format("Response: 422, %s", e.getMessage()), e);
            ctx.fail(ENTITY_UNPARSABLE, e);
        } catch (SessionExpired e) {
            LOGGER.warn(String.format("Response: 404, %s", e.getMessage()), e);
            ctx.response().setStatusCode(NOT_FOUND).end(); // client sends a new pre-request for this upload
        } catch (SkipUpload e) {
            LOGGER.debug(e.getMessage(), e);
            remove(session); // client won't resume
            ctx.fail(PRECONDITION_FAILED, e);
        } catch (RuntimeException e) {
            ctx.fail(e);
        }
    }

    /**
     * Handles the chunk upload request when there was no chunk file found on the server yet.
     *
     * @param ctx The Vertx <code>RoutingContext</code> used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     * @param path {@code String} if the session contained a path to the chunk file or {@code null} otherwise.
     */
    private void handleFirstChunkUpload(final RoutingContext ctx, final HttpServerRequest request,
            final Session session, final User user, final ContentRange contentRange, final RequestMetaData metaData,
            final String path) {

        if (!contentRange.fromIndex.equals("0")) {
            if (path == null) {
                // I.e. the server received data in a previous request but now cannot find the `path` session value.
                // Unsure when this can happen. Not accepting the data. Asking to restart upload (`404`).
                LOGGER.warn(
                        String.format("Response: 404, path is null and unexpected content range: %s", contentRange));
            } else {
                // Server received data in a previous request but the chunk file was probably cleaned by cleaner task.
                // Can't return 308. Google API client lib throws `Preconditions` on subsequent chunk upload.
                // This makes sense as the server reported before that bytes (>0) were received.
                LOGGER.warn(String.format("Response: 404, Unexpected content range: %s", contentRange));
            }
            ctx.response().setStatusCode(NOT_FOUND).end(); // client sends a new pre-request for this upload
            return;
        }
        acceptUpload(ctx, request, session, user, contentRange, metaData);
    }

    // Cache found, expecting upload to continue the cached file

    /**
     *
     * @param ctx The Vertx <code>RoutingContext</code> used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     * @param path {@code String} if the session contained a path to the chunk file or {@code null} otherwise.
     */
    private void handleSubsequentChunkUpload(final RoutingContext ctx, final HttpServerRequest request,
            final Session session, final User user, final ContentRange contentRange, final RequestMetaData metaData,
            final String path) {

        request.pause();
        final var fs = ctx.vertx().fileSystem();
        fs.props(path).onSuccess(props -> {
            request.resume();
            // Wrong chunk uploaded
            final var byteSize = props.size();
            if (!contentRange.fromIndex.equals(String.valueOf(byteSize))) {
                // Ask client to resume from the correct position
                final var range = String.format("bytes=0-%d", byteSize - 1);
                LOGGER.debug(String.format("Response: 308, Range %s (partial data)", range));
                ctx.response().putHeader("Range", range);
                ctx.response().putHeader("Content-Length", "0");
                ctx.response().setStatusCode(RESUME_INCOMPLETE).end();
                return;
            }
            acceptUpload(ctx, request, session, user, contentRange, new File(path), metaData);
        }).onFailure(failure -> {
            LOGGER.error("Response: 500, failed to read props from temp file");
            ctx.fail(500, failure);
        });
    }

    /**
     * Extracts the metadata from the request header.
     *
     * @param request the request to extract the data from
     * @return the extracted metadata
     * @throws SkipUpload when the server is not interested in the uploaded data
     * @throws InvalidMetaData when the request is missing metadata fields
     */
    private RequestMetaData metaData(final HttpServerRequest request) throws InvalidMetaData, SkipUpload {

        try {
            // Identifiers
            final var deviceId = request.getHeader(FormAttributes.DEVICE_ID.getValue());
            final var measurementId = request.getHeader(FormAttributes.MEASUREMENT_ID.getValue());
            if (measurementId == null || deviceId == null) {
                throw new InvalidMetaData("Measurement- and/or DeviceId missing in header");
            }

            // Location info
            final var locationCount = Long.parseLong(request.getHeader(FormAttributes.LOCATION_COUNT.getValue()));
            if (locationCount < MINIMUM_LOCATION_COUNT) {
                throw new SkipUpload(String.format("Too few location points %s", locationCount));
            }
            final var startLocationLatString = request.getHeader(FormAttributes.START_LOCATION_LAT.getValue());
            final var startLocationLonString = request.getHeader(FormAttributes.START_LOCATION_LON.getValue());
            final var startLocationTsString = request.getHeader(FormAttributes.START_LOCATION_TS.getValue());
            final var endLocationLatString = request.getHeader(FormAttributes.END_LOCATION_LAT.getValue());
            final var endLocationLonString = request.getHeader(FormAttributes.END_LOCATION_LON.getValue());
            final var endLocationTsString = request.getHeader(FormAttributes.END_LOCATION_TS.getValue());
            if (startLocationLatString == null || startLocationLonString == null || startLocationTsString == null
                    || endLocationLatString == null || endLocationLonString == null) {
                throw new InvalidMetaData("Start-/end location data incomplete!");
            }
            final var startLocationLat = Double.parseDouble(startLocationLatString);
            final var startLocationLon = Double.parseDouble(startLocationLonString);
            final var startLocationTs = Long.parseLong(startLocationTsString);
            final var endLocationLat = Double.parseDouble(endLocationLatString);
            final var endLocationLon = Double.parseDouble(endLocationLonString);
            final var endLocationTs = Long.parseLong(endLocationTsString);
            final var startLocation = new RequestMetaData.GeoLocation(startLocationTs, startLocationLat,
                    startLocationLon);
            final var endLocation = new RequestMetaData.GeoLocation(endLocationTs, endLocationLat, endLocationLon);

            // Format version
            final var formatVersionString = request.getHeader(FormAttributes.FORMAT_VERSION.getValue());
            if (formatVersionString == null) {
                throw new InvalidMetaData("Data incomplete!");
            }
            final var formatVersion = Integer.parseInt(formatVersionString);

            // Etc.
            final var deviceType = request.getHeader(FormAttributes.DEVICE_TYPE.getValue());
            final var osVersion = request.getHeader(FormAttributes.OS_VERSION.getValue());
            final var applicationVersion = request.getHeader(FormAttributes.APPLICATION_VERSION.getValue());
            final var length = Double.parseDouble(request.getHeader(FormAttributes.LENGTH.getValue()));
            final var modality = request.getHeader(FormAttributes.MODALITY.getValue());
            return new RequestMetaData(deviceId, measurementId, osVersion, deviceType, applicationVersion,
                    length, locationCount, startLocation, endLocation, modality, formatVersion);
        } catch (final IllegalArgumentException | NullPointerException e) {
            throw new InvalidMetaData("Data was not parsable!", e);
        }
    }

    /**
     * Extracts the content range information from the request header and checks it matches the body size information
     * from the header.
     *
     * @param request the request to check the header for
     * @param bodySize the number of bytes to be uploaded
     * @return the extracted content range information
     * @throws Unparsable if the content range does not match the body size information
     */
    private ContentRange contentRange(final HttpServerRequest request, long bodySize) throws Unparsable {

        // The client informs what data is attached: `bytes fromIndex-toIndex/totalBytes`
        final var contentRangeString = request.getHeader("Content-Range");
        if (!contentRangeString.matches("bytes [0-9]+-[0-9]+/[0-9]+")) {
            throw new Unparsable(String.format("Content-Range request not supported: %s", contentRangeString));
        }
        final var startingWithFrom = contentRangeString.substring(6);
        final var dashPosition = startingWithFrom.indexOf('-');
        Validate.isTrue(dashPosition != -1);
        final var from = startingWithFrom.substring(0, dashPosition);
        final var startingWithTo = startingWithFrom.substring(dashPosition + 1);
        final var slashPosition = startingWithTo.indexOf('/');
        Validate.isTrue(slashPosition != -1);
        final var to = startingWithTo.substring(0, slashPosition);
        final var total = startingWithTo.substring(slashPosition + 1);
        final var contentRange = new ContentRange(from, to, total);

        // Make sure content-length matches the content range (to-from+1)
        if (bodySize != (Long.parseLong(contentRange.toIndex) - Long.parseLong(contentRange.fromIndex) + 1)) {
            throw new Unparsable(String.format("Upload size (%d) does not match content rang of header (%s)!",
                    bodySize, contentRange));
        }
        return contentRange;
    }

    /**
     * Creates a new upload temp file and calls
     * {@link #acceptUpload(RoutingContext, HttpServerRequest, Session, User, ContentRange, File, RequestMetaData)}.
     *
     * @param ctx The Vertx <code>RoutingContext</code> used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     */
    private void acceptUpload(final RoutingContext ctx, final HttpServerRequest request, final Session session,
            final User user, final ContentRange contentRange, final RequestMetaData metaData) {

        // Create temp file to accept binary
        final var fileName = UUID.randomUUID().toString();
        final var uploadFolder = FILE_UPLOADS_FOLDER.toFile();
        if (!uploadFolder.exists()) {
            Validate.isTrue(uploadFolder.mkdir());
        }
        final var tempFile = Paths.get(uploadFolder.getPath(), fileName).toAbsolutePath().toFile();

        // Bind session to this measurement and mark as "pre-request accepted"
        session.put(UPLOAD_PATH_FIELD, tempFile);

        acceptUpload(ctx, request, session, user, contentRange, tempFile, metaData);
    }

    /**
     * Streams the request body into a temp file and persists the file after it's fully uploaded.
     *
     * @param ctx The Vertx <code>RoutingContext</code> used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     */
    private void acceptUpload(final RoutingContext ctx, final HttpServerRequest request, final Session session,
            final User user, final ContentRange contentRange, final File tempFile, final RequestMetaData metaData) {

        final var fs = ctx.vertx().fileSystem();
        request.pause();
        fs.open(tempFile.getAbsolutePath(), new OpenOptions().setAppend(true)).onSuccess(asyncFile -> {
            request.resume();

            // Pipe body to reduce memory usage and store body of interrupted connections (to support resume)
            request.pipeTo(asyncFile).onSuccess(success -> {
                // Check if the upload is complete or if this was just a chunk
                fs.props(tempFile.toString()).onSuccess(props -> {

                    // We could reuse information but to be sure we check the actual file size
                    final var byteSize = props.size();
                    if (byteSize - 1 != Long.parseLong(contentRange.toIndex)) {
                        LOGGER.error(String.format(
                                "Response: 500, Content-Range (%s) not matching file size (%d - 1)",
                                contentRange, byteSize));
                        ctx.fail(500);
                        return;
                    }

                    // This was not the final chunk of data
                    if (Long.parseLong(contentRange.toIndex) != Long.parseLong(contentRange.totalBytes) - 1) {
                        // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
                        final var range = String.format("bytes=0-%d", byteSize - 1);
                        LOGGER.debug(String.format("Response: 308, Range %s", range));
                        ctx.response().putHeader("Range", range);
                        ctx.response().putHeader("Content-Length", "0");
                        ctx.response().setStatusCode(RESUME_INCOMPLETE).end();
                        return;
                    }

                    // Persist data
                    final var measurement = new Measurement(metaData, user.getIdString(), tempFile);
                    storeToMongoDB(measurement, ctx);
                }).onFailure(failure -> {
                    LOGGER.error("Response: 500, failed to read props from temp file");
                    ctx.fail(500, failure);
                });
            }).onFailure(failure -> {
                if (failure.getClass().equals(PayloadTooLarge.class)) {
                    remove(session, tempFile); // client won't resume
                    LOGGER.error(String.format("Response: 422: %s", failure.getMessage()), failure);
                    ctx.fail(ENTITY_UNPARSABLE, failure.getCause());
                    return;
                }
                // Not cleaning session/uploads to allow resume
                LOGGER.error(String.format("Response: 500: %s", failure.getMessage()), failure);
                ctx.fail(500, failure);
            });
        }).onFailure(failure -> {
            LOGGER.error("Unable to open temporary file to stream request to!", failure);
            ctx.fail(500, failure);
        });
    }

    /**
     * Stores a {@link Measurement} to a Mongo database. This method never fails. If a failure occurs it is logged and
     * status code 422 is used for the response.
     *
     * @param measurement The measured data to write to the Mongo database
     * @param ctx The Vertx <code>RoutingContext</code> used to write the response
     */
    public void storeToMongoDB(final Measurement measurement, final RoutingContext ctx) {
        LOGGER.debug("Inserted measurement with id {}:{}!", measurement.getMetaData().getDeviceIdentifier(),
                measurement.getMetaData().getMeasurementIdentifier());

        mongoClient.createDefaultGridFsBucketService().onSuccess(gridFs -> {

            final var fileSystem = ctx.vertx().fileSystem();
            final var fileUpload = measurement.getBinary();
            final var openFuture = fileSystem.open(fileUpload.getAbsolutePath(), new OpenOptions());
            final var uploadFuture = openFuture.compose(file -> {
                final var options = new GridFsUploadOptions();
                final var metaData = measurement.toJson();
                options.setMetadata(new JsonObject(metaData.toString()));
                return gridFs.uploadByFileNameWithOptions(file, fileUpload.getName(), options);
            });

            // Wait for all file uploads to complete
            uploadFuture.onSuccess(result -> {
                // Not removing session to allow the client to check the upload status if interrupted
                LOGGER.debug("Response: 201");
                clean(ctx.session(), measurement.getBinary());
                ctx.response().setStatusCode(201).end();
            }).onFailure(cause -> {
                LOGGER.error("Unable to store file to MongoDatabase!", cause);
                ctx.fail(500, cause);
            });
        }).onFailure(cause -> {
            LOGGER.error("Unable to open connection to Mongo Database!", cause);
            ctx.fail(500, cause);
        });
    }

    /**
     * Checks if the session is considered "valid", i.e. there was a pre-request which was accepted by the server with
     * the same identifiers as in this request.
     *
     * @param session the session to be checked
     * @param metaData the identifier of this request header
     * @throws IllegalSession if the device-/measurement id of this request does not match the one of the pre-request
     * @throws SessionExpired if the session is not found, e.g. because it expired
     */
    private void checkSessionValidity(final Session session, final RequestMetaData metaData)
            throws IllegalSession, SessionExpired {

        // Ensure this session was accepted by PreRequestHandler and bound to this measurement
        final String sessionMeasurementId = session.get(MEASUREMENT_ID_FIELD);
        final String sessionDeviceId = session.get(DEVICE_ID_FIELD);
        if (sessionMeasurementId == null || sessionDeviceId == null) {
            throw new SessionExpired("Mid/did missing, session maybe expired, request upload restart (404).");
        }
        if (!sessionMeasurementId.equals(metaData.getMeasurementIdentifier())) {
            throw new IllegalSession(String.format("Unexpected measurement id: %s.", sessionMeasurementId));
        }
        if (!sessionDeviceId.equals(metaData.getDeviceIdentifier())) {
            throw new IllegalSession(String.format("Unexpected device id: %s.", sessionDeviceId));
        }
    }

    private void remove(final Session session, final File upload) {
        remove(session);
        remove(upload);
    }

    private void clean(final Session session, final File upload) {
        // not removing session
        session.remove(UPLOAD_PATH_FIELD);
        remove(upload);
    }

    private void remove(final File upload) {
        final var deleted = upload.delete();
        Validate.isTrue(deleted);
    }

    private void remove(final Session session) {
        session.destroy();
    }

    /**
     * The content range information as transmitted by the request header.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 6.0.0
     */
    private static class ContentRange {
        final String fromIndex;
        final String toIndex;
        final String totalBytes;

        public ContentRange(String fromIndex, String toIndex, String totalBytes) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.totalBytes = totalBytes;
        }

        @Override
        public String toString() {
            return "ContentRange{" +
                    "fromIndex='" + fromIndex + '\'' +
                    ", toIndex='" + toIndex + '\'' +
                    ", totalBytes='" + totalBytes + '\'' +
                    '}';
        }
    }
}
