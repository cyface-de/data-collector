package de.cyface.collector.model.metadata

import de.cyface.collector.handler.FormAttributes
import de.cyface.collector.model.metadata.MetaData.Companion.MAX_GENERIC_METADATA_FIELD_LENGTH
import io.vertx.core.json.JsonObject
import java.io.Serializable

/**
 * The metadata which describes the device which collected the data.
 *
 * @author Armin Schnabel
 * @property operatingSystemVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
 * @property deviceType The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
 */
data class DeviceMetaData(
    val operatingSystemVersion: String,
    val deviceType: String,
) : MetaData, Serializable {

    init {
        require(
            operatingSystemVersion.isNotEmpty() &&
                operatingSystemVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH
        ) {
            "Field osVersion had an invalid length of ${operatingSystemVersion.length.toLong()}"
        }
        require(deviceType.isNotEmpty() && deviceType.length <= MAX_GENERIC_METADATA_FIELD_LENGTH) {
            "Field deviceType had an invalid length of ${deviceType.length.toLong()}"
        }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.OS_VERSION.value, operatingSystemVersion)
        ret.put(FormAttributes.DEVICE_TYPE.value, deviceType)
        return ret
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
