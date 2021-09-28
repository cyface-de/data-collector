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
import static de.cyface.collector.handler.MeasurementHandler.UPLOAD_PATH_FIELD;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.handler.exception.InvalidMetaData;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for receiving HTTP PUT requests with an empty body on the "measurements" end point.
 * <p>
 * This request type is used by clients to ask the upload status and, thus, where to continue the upload.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
public class StatusHandler implements Handler<RoutingContext> {

    /**
     * The logger for objects of this class. You can change its configuration by
     * adapting the values in <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);
    /**
     * HTTP status code to return when the client asks to resume an upload and the server replies where to continue.
     */
    static final int RESUME_INCOMPLETE = 308;
    /**
     * Vertx <code>MongoClient</code> used to access the database to write the received data to.
     */
    private final MongoClient dataClient;

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param dataClient Vertx <code>MongoClient</code> used to access the database to write the received data to.
     */
    public StatusHandler(MongoClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void handle(RoutingContext ctx) {
        LOGGER.info("Received new upload status request.");
        final var request = ctx.request();
        final var session = ctx.session();

        // Only accepting requests when client knows the file size
        // i.e. `Content-Range` headers with `bytes */SIZE` but not `bytes */*`
        final var rangeRequest = request.getHeader("Content-Range");
        if (!rangeRequest.matches("bytes \\*/[0-9]+")) {
            LOGGER.error(String.format("Content-Range request not supported: %s", rangeRequest));
            ctx.fail(ENTITY_UNPARSABLE);
            return;
        }

        try {
            // Check if measurement already exists in database
            final var deviceId = request.getHeader(FormAttributes.DEVICE_ID.getValue());
            final var measurementId = request.getHeader(FormAttributes.MEASUREMENT_ID.getValue());
            if (deviceId == null || measurementId == null) {
                throw new InvalidMetaData("Data incomplete!");
            }
            final var access = dataClient.createDefaultGridFsBucketService();
            access.onSuccess(gridFs -> {
                final var query = new JsonObject();
                query.put("metadata.deviceId", deviceId);
                query.put("metadata.measurementId", measurementId);
                gridFs.findIds(query)
                        .onFailure(failure -> {
                            LOGGER.error(failure.getMessage(), failure);
                            ctx.fail(500, failure);
                        })
                        .onSuccess(success -> {

                            if (success.size() > 1) {
                                LOGGER.error(String.format("More than one measurement found for did %s mid %s",
                                        deviceId, measurementId));
                                ctx.fail(500);
                            }
                            if (success.size() == 1) {
                                LOGGER.debug("Response: 200, measurement already exists, no upload needed");
                                ctx.response().setStatusCode(200).end();
                            }

                            // If no bytes have been received, return 308 but without a `Range` header to indicate this
                            final var path = session.get(UPLOAD_PATH_FIELD);
                            if (path == null) {
                                LOGGER.debug("Response: 308, no Range (no path)");
                                ctx.response().putHeader("Content-Length", "0");
                                ctx.response().setStatusCode(RESUME_INCOMPLETE).end();
                                return;
                            }

                            // Check chunk size to reply where to continue the upload
                            final var fileSystem = ctx.vertx().fileSystem();
                            fileSystem.exists(path.toString(), exists -> {
                                if (exists.succeeded()) {

                                    final var fileExists = exists.result();
                                    if (!fileExists) {
                                        // As this links to a non-existing file, remove this
                                        session.remove(UPLOAD_PATH_FIELD);

                                        // If no bytes have been received, return 308 but without a `Range` header to
                                        // indicate this
                                        LOGGER.debug("Response: 308, no Range (path, no file)");
                                        ctx.response().putHeader("Content-Length", "0");
                                        ctx.response().setStatusCode(RESUME_INCOMPLETE).end();
                                        return;
                                    }

                                    fileSystem.props(path.toString(), props -> {
                                        if (props.succeeded()) {
                                            final var fileProps = props.result();
                                            final var byteSize = fileProps.size();

                                            // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
                                            final var range = String.format("bytes=0-%d", byteSize - 1);
                                            LOGGER.debug(String.format("Response: 308, Range %s", range));
                                            ctx.response().putHeader("Range", range);
                                            ctx.response().putHeader("Content-Length", "0");
                                            ctx.response().setStatusCode(RESUME_INCOMPLETE).end();
                                        } else {
                                            ctx.fail(500, props.cause());
                                        }
                                    });
                                } else {
                                    ctx.fail(500, exists.cause());
                                }
                            });
                        });
            });
        } catch (InvalidMetaData e) {
            LOGGER.error(e.getMessage(), e);
            ctx.fail(ENTITY_UNPARSABLE);
        }
    }
}
