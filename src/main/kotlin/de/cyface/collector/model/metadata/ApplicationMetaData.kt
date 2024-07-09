package de.cyface.collector.model.metadata

import de.cyface.collector.handler.FormAttributes
import de.cyface.collector.handler.exception.DeprecatedFormatVersion
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.model.metadata.MetaData.Companion.MAX_GENERIC_METADATA_FIELD_LENGTH
import io.vertx.core.json.JsonObject
import java.io.Serializable

/**
 * The metadata which describes the application which collected the data.
 *
 * @author Armin Schnabel
 * @property applicationVersion The version of the app that transmitted the measurement.
 * @property formatVersion The format version of the upload file.
 */
data class ApplicationMetaData(
    val applicationVersion: String,
    val formatVersion: Int,
) : MetaData, Serializable {
    init {
        require(applicationVersion.isNotEmpty() && applicationVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH) {
            "Field applicationVersion had an invalid length of ${applicationVersion.length.toLong()}"
        }
        if (formatVersion < CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
            throw DeprecatedFormatVersion("Deprecated formatVersion: ${formatVersion.toLong()}")
        } else if (formatVersion != CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
            throw UnknownFormatVersion("Unknown formatVersion: ${formatVersion.toLong()}")
        }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.APPLICATION_VERSION.value, applicationVersion)
        ret.put(FormAttributes.FORMAT_VERSION.value, formatVersion)
        return ret
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L

        /**
         * The current version of the transferred file. This is always specified by the first two bytes of the file
         * transferred and helps compatible APIs to process data from different client versions.
         */
        const val CURRENT_TRANSFER_FILE_FORMAT_VERSION = 3
    }
}
