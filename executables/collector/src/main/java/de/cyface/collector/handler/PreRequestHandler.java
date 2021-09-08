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
import static de.cyface.collector.handler.MeasurementHandler.PAYLOAD_TOO_LARGE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.handler.exception.InvalidMetaData;
import de.cyface.collector.handler.exception.PayloadTooLarge;
import de.cyface.collector.handler.exception.SkipUpload;
import de.cyface.collector.handler.exception.Unparsable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

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
     * The maximal number of {@code Byte}s which may be uploaded in the upload request.
     */
    private final long measurementLimit;

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param measurementLimit The maximal number of {@code Byte}s which may be uploaded in the upload request.
     */
    public PreRequestHandler(final long measurementLimit) {
        this.measurementLimit = measurementLimit;
    }

    @Override
    public void handle(RoutingContext ctx) {
        LOGGER.info("Received new pre-request.");
        final var request = ctx.request();
        // Do not use `request.body()` as the Vert.X `BodyHandler` already read the body and throws 403 [DAT-749]
        final var metaData = ctx.getBodyAsJson();
        try {
            checkUploadSize(request.headers(), measurementLimit, X_UPLOAD_CONTENT_LENGTH_FIELD);
            check(metaData);
        } catch (final InvalidMetaData | Unparsable e) {
            LOGGER.error(e.getMessage(), e);
            ctx.fail(ENTITY_UNPARSABLE);
            return;
        } catch (SkipUpload e) {
            LOGGER.debug(e.getMessage(), e);
            // Ask the client to skip the upload, e.g. b/c of geo-fencing, deprecated Binary, missing locations, ...
            // We can add information to the response body to distinguish different causes.
            ctx.fail(PRECONDITION_FAILED);
            return;
        } catch (PayloadTooLarge e) {
            LOGGER.debug(e.getMessage(), e);
            ctx.fail(PAYLOAD_TOO_LARGE);
            return;
        }

        // We don't support resumable uploads yet, thus the Location needs to equal the pre-request URL
        // Otherwise Location should contain the upload id: "https://entpoint?uploadType=resumable&upload_id=*****";
        final var requestUri = request.absoluteURI();
        final var protocol = request.getHeader("X-Forwarded-Proto");
        final var locationUri = protocol != null ? requestUri.replace("http:", protocol + ":") : requestUri;
        LOGGER.debug("Returning Location: " + locationUri);
        ctx.response()
                .putHeader("Location", locationUri)
                .putHeader("Content-Length", "0")
                .setStatusCode(200).end();
    }

    /**
     * Checks if the information about the upload size in the header exceeds the {@param measurementLimit}.
     *
     * @param headers The header to check.
     * @param measurementLimit The maximal number of {@code Byte}s which may be uploaded in the upload request.
     * @param uploadLengthField The name of the header field to check.
     * @throws Unparsable If the header is missing the expected field about the upload size.
     * @throws PayloadTooLarge If the requested upload is too large.
     */
    static void checkUploadSize(final MultiMap headers, final long measurementLimit, String uploadLengthField)
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
        } catch (final IllegalArgumentException | NullPointerException e) {
            throw new InvalidMetaData("Data was not parsable!", e);
        }
    }
}
