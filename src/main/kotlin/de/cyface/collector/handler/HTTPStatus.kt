/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.collector.handler

/**
 * Provides the different HTTP status codes used by the Cyface data collector, as readable constants.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
object HTTPStatus {
    /**
     * A request was successfully handled.
     */
    const val OK = 200

    /**
     * An entity - for example a measurement - was successfully created as a result of the request.
     */
    const val CREATED = 201

    /**
     * The request was malformed somehow.
     */
    const val BAD_REQUEST = 400

    /**
     * The user that tried to access the resource is unauthorized.
     */
    const val UNAUTHORIZED = 401

    /**
     * HTTP status code to return when the client tries to resume an upload but the session has expired.
     */
    const val NOT_FOUND = 404

    /**
     * Http code which indicates that the upload request syntax was incorrect.
     */
    const val ENTITY_UNPARSABLE = 422

    /**
     * Http code which indicates that the upload intended by the client should be skipped.
     *
     *
     * The server is not interested in the data, e.g. missing location data or data from a location of no interest.
     */
    const val PRECONDITION_FAILED = 412

    /**
     * Reported to the client, if the measurement to upload has already been received. It should not be retransmitted.
     */
    const val HTTP_CONFLICT = 409

    /**
     * This status code is returned if the user that tried to access the collector has not been activated yet.
     */
    const val PRECONDITION_REQUIRED = 428

    /**
     * On any internal server error, where we do not want to provide additional information.
     * Details should be available via the server logs.
     */
    const val SERVER_ERROR = 500

    /**
     * HTTP status code to return when the client asks to resume an upload and the server replies where to continue.
     */
    const val RESUME_INCOMPLETE = 308
}
