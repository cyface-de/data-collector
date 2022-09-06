package de.cyface.collector.handler

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
     * On any internal server error, where we do not want to provide additional information.
     * Details should be available via the server logs.
     */
    const val SERVER_ERROR = 500

    /**
     * HTTP status code to return when the client asks to resume an upload and the server replies where to continue.
     */
    const val RESUME_INCOMPLETE = 308
}