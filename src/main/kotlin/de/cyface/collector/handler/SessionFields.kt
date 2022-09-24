package de.cyface.collector.handler

object SessionFields {
    /**
     * The field name for the session entry which contains the path of the temp file containing the upload binary.
     *
     * This field is set in the [MeasurementHandler] to support resumable upload.
     */
    const val UPLOAD_PATH_FIELD = "upload-path"
}