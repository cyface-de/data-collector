/*
 * Copyright 2021 Cyface GmbH
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

import static de.cyface.api.MongoAuthHandler.ENTITY_UNPARSABLE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.handler.exception.IllegalSession;
import de.cyface.collector.handler.exception.InvalidMetaData;
import de.cyface.collector.handler.exception.PayloadTooLarge;
import de.cyface.collector.handler.exception.SkipUpload;
import de.cyface.collector.handler.exception.Unparsable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

/**
 * A handler for receiving HTTP POST requests on the "measurements" end point.
 * This end point tells the client if the upload may continue or should be skipped.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
public class PreRequestHandler implements Handler<RoutingContext> {

    /**
     * The <code>Logger</code> used for objects of this class. Configure it by changing the settings in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PreRequestHandler.class);

    /**
     * The minimum amount of location points required to accept an upload.
     */
    public static final int MINIMUM_LOCATION_COUNT = 2;

    /**
     * Http code which indicates that the upload intended by the client should be skipped.
     * <p>
     * The server is not interested in the data, e.g. missing location data or data from a location of no interest.
     */
    public static final int PRECONDITION_FAILED = 412;
    public static final int HTTP_CONFLICT = 409;

    /**
     * The current version of the transferred file. This is always specified by the first two bytes of the file
     * transferred and helps compatible APIs to process data from different client versions.
     */
    public static final int CURRENT_TRANSFER_FILE_FORMAT_VERSION = 2;

    /**
     * The header field which contains the number of bytes of the "requested" upload.
     */
    private static final String X_UPLOAD_CONTENT_LENGTH_FIELD = "x-upload-content-length";

    /**
     * The field name for the session entry which contains the measurement id.
     * <p>
     * This field is set in the {@link PreRequestHandler} to make sure sessions are bound to measurements and uploads
     * are only accepted with an accepted pre request.
     */
    static final String MEASUREMENT_ID_FIELD = "measurement-id";

    /**
     * The field name for the session entry which contains the device id.
     * <p>
     * This field is set in the {@link PreRequestHandler} to make sure sessions are bound to measurements and uploads
     * are only accepted with an accepted pre request.
     */
    static final String DEVICE_ID_FIELD = "device-id";

    /**
     * The maximal number of {@code Byte}s which may be uploaded in the upload request.
     */
    private final long measurementLimit;
    /**
     * Vertx <code>MongoClient</code> used to access the database to write the received data to.
     */
    private final MongoClient dataClient;

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param dataDatabase Vertx <code>MongoClient</code> used to access the database to write the received data to.
     * @param measurementLimit The maximal number of {@code Byte}s which may be uploaded in the upload request.
     */
    public PreRequestHandler(final MongoClient dataDatabase, final long measurementLimit) {
        this.dataClient = dataDatabase;
        this.measurementLimit = measurementLimit;
    }

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.info("Received new pre-request.");
        final var request = ctx.request();
        final var session = ctx.session();

        // Do not use `request.body()` as the Vert.X `BodyHandler` already reads the body and throws 403 [DAT-749]
        final var metaData = ctx.getBodyAsJson();
        try {
            bodySize(request.headers(), measurementLimit, X_UPLOAD_CONTENT_LENGTH_FIELD);
            check(metaData);
            check(session);

            // Check if measurement already exists in database
            final var measurementId = metaData.getString(FormAttributes.MEASUREMENT_ID.getValue());
            final var deviceId = metaData.getString(FormAttributes.DEVICE_ID.getValue());
            if (measurementId == null || deviceId == null) {
                throw new InvalidMetaData("Data incomplete!");
            }
            final var access = dataClient.createDefaultGridFsBucketService();
            access.onSuccess(gridFs -> {
                try {
                    final var query = new JsonObject();
                    query.put("metadata.deviceId", deviceId);
                    query.put("metadata.measurementId", measurementId);
                    final var findIds = gridFs.findIds(query);
                    findIds.onFailure(failure -> {
                        LOGGER.error(failure.getMessage(), failure);
                        ctx.fail(500, failure);
                    });
                    findIds.onSuccess(ids -> {
                        try {
                            if (ids.size() > 1) {
                                LOGGER.error(String.format("More than one measurement found for did %s mid %s",
                                        deviceId, measurementId));
                                ctx.fail(500);
                                return;
                            }
                            if (ids.size() == 1) {
                                LOGGER.debug("Response: 409, measurement already exists, no upload needed");
                                ctx.response().setStatusCode(HTTP_CONFLICT).end();
                                return;
                            }

                            // Bind session to this measurement and mark as "pre-request accepted"
                            session.put(MEASUREMENT_ID_FIELD, measurementId);
                            session.put(DEVICE_ID_FIELD, deviceId);

                            // Google uses the `Location` format:
                            // `https://host/endpoint?uploadType=resumable&upload_id=SESSION_ID`
                            // To use the Vert.X session parsing we use:
                            // `https://host/endpoint?uploadType=resumable&upload_id=(SESSION_ID)"
                            final var requestUri = request.absoluteURI();
                            final var protocol = request.getHeader("X-Forwarded-Proto");
                            final var locationUri = locationUri(requestUri, protocol, session.id());
                            LOGGER.debug("Response 200, Location: " + locationUri);
                            ctx.response()
                                    .putHeader("Location", locationUri)
                                    .putHeader("Content-Length", "0")
                                    .setStatusCode(200).end();
                        } catch (RuntimeException e) {
                            ctx.fail(e);
                        }
                    });
                } catch (RuntimeException e) {
                    ctx.fail(e);
                }
            });
        } catch (final InvalidMetaData | Unparsable | IllegalSession | PayloadTooLarge e) {
            LOGGER.error(e.getMessage(), e);
            ctx.fail(ENTITY_UNPARSABLE);
        } catch (SkipUpload e) {
            LOGGER.debug(e.getMessage(), e);
            // Ask the client to skip the upload, e.g. b/c of geo-fencing, deprecated Binary, missing locations, ...
            // We can add information to the response body to distinguish different causes.
            ctx.fail(PRECONDITION_FAILED);
        }
    }

    /**
     * Checks that no pre-existing session was passed in the pre-request.
     *
     * @param session the session to check
     * @throws IllegalSession if an existing session was passed
     */
    private void check(final Session session) throws IllegalSession {
        // The purpose of the `pre-request` is to generate an upload session for `upload requests`.
        // Thus, we're expecting that the `SessionHandler` automatically created a new session for this pre-request.
        final var measurementId = session.get(MEASUREMENT_ID_FIELD);
        final var deviceId = session.get(DEVICE_ID_FIELD);
        if (measurementId != null) {
            throw new IllegalSession(String.format("Unexpected measurement id: %s.", measurementId));
        }
        if (deviceId != null) {
            throw new IllegalSession(String.format("Unexpected device id: %s.", deviceId));
        }
    }

    /**
     * Assembles the {@code Uri} for the `Location` header required by the client who sent the upload request.
     * <p>
     * This contains the upload session id which is needed to start an upload.
     *
     * @param requestUri the {@code Uri} to which the upload request was sent.
     * @param protocol the protocol used in the upload request as defined in the request header `X-Forwarded-Proto`
     * @param sessionId the upload session id to be added to the `Location` header assembled.
     * @return The assembled `Location` {@code Uri} to be returned to the client
     */
    private String locationUri(final String requestUri, final String protocol, final String sessionId) {
        // Our current setup forwards https requests to http internally. As the `Location` returned is automatically
        // used by the Google Client API library for the upload request we make sure the original protocol use returned.
        final var protocolReplaced = protocol != null ? requestUri.replace("http:", protocol + ":") : requestUri;
        // The Google Client API library automatically adds the parameter `uploadType` to the Uri. As our upload API
        // offers an endpoint address in the format `measurement/(SID)` we remove the `uploadType` parameter.
        // We don't need to process the `uploadType` as we're only offering one upload type: resumable.
        final var uploadTypeRemoved = protocolReplaced.replace("?uploadType=resumable", "");
        return String.format("%s/(%s)/", uploadTypeRemoved, sessionId);
    }

    /**
     * Checks if the information about the upload size in the header exceeds the {@param measurementLimit}.
     *
     * @param headers The header to check.
     * @param measurementLimit The maximal number of {@code Byte}s which may be uploaded in the upload request.
     * @param uploadLengthField The name of the header field to check.
     * @return the number of bytes to be uploaded
     * @throws Unparsable If the header is missing the expected field about the upload size.
     * @throws PayloadTooLarge If the requested upload is too large.
     */
    static long bodySize(final MultiMap headers, final long measurementLimit, String uploadLengthField)
            throws Unparsable, PayloadTooLarge {
        if (!headers.contains(uploadLengthField)) {
            throw new Unparsable(String.format("The header is missing the field %s", uploadLengthField));
        }
        final var uploadLengthString = headers.get(uploadLengthField);
        try {
            final var uploadLength = Long.parseLong(uploadLengthString);
            if (uploadLength > measurementLimit) {
                throw new PayloadTooLarge(
                        String.format("Upload size in the pre-request (%d) is too large, limit is %d bytes.",
                                uploadLength, measurementLimit));
            }
            return uploadLength;
        } catch (NumberFormatException e) {
            throw new Unparsable(
                    String.format("The header field %s is unparsable: %s", uploadLengthField, uploadLengthString));
        }
    }

    /**
     * Checks the metadata in the {@param body}.
     *
     * @param body The request body to check for the expected metadata fields.
     * @throws InvalidMetaData If the metadata is in an invalid format.
     * @throws SkipUpload If the server is not interested in the data.
     * @throws Unparsable E.g. if there is a syntax error in the body.
     */
    private void check(final JsonObject body) throws InvalidMetaData, SkipUpload, Unparsable {
        try {
            final var locationCount = Long
                    .parseLong(body.getString(FormAttributes.LOCATION_COUNT.getValue()));
            if (locationCount < MINIMUM_LOCATION_COUNT) {
                throw new SkipUpload(String.format("Too few location points %s", locationCount));
            }

            final var formatVersion = Integer.parseInt(body.getString(FormAttributes.FORMAT_VERSION.getValue()));
            if (formatVersion != CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
                throw new SkipUpload(String.format("Unsupported format version: %s", formatVersion));
            }

            final var startLocationLat = body.getString(FormAttributes.START_LOCATION_LAT.getValue());
            final var startLocationLon = body.getString(FormAttributes.START_LOCATION_LON.getValue());
            final var startLocationTs = body.getString(FormAttributes.START_LOCATION_TS.getValue());
            final var endLocationLat = body.getString(FormAttributes.END_LOCATION_LAT.getValue());
            final var endLocationLon = body.getString(FormAttributes.END_LOCATION_LON.getValue());
            final var endLocationTs = body.getString(FormAttributes.END_LOCATION_TS.getValue());

            if (startLocationLat == null || startLocationLon == null || startLocationTs == null
                    || endLocationLat == null || endLocationLon == null || endLocationTs == null) {
                throw new InvalidMetaData("Data incomplete!");
            }

            final var measurementId = body.getString(FormAttributes.MEASUREMENT_ID.getValue());
            final var deviceId = body.getString(FormAttributes.DEVICE_ID.getValue());
            if (measurementId == null || deviceId == null) {
                throw new InvalidMetaData("Data incomplete!");
            }
        } catch (final IllegalArgumentException | NullPointerException e) {
            throw new InvalidMetaData("Data was not parsable!", e);
        }
    }
}
