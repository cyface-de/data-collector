package de.cyface.collector.model.metadata

import de.cyface.collector.handler.FormAttributes
import io.vertx.core.json.JsonObject
import java.io.Serializable

/**
 * The metadata which describes the attachments collected together with the measurement.
 *
 * @author Armin Schnabel
 * @property logCount Number of log files captured for this measurement, e.g. image capturing metrics.
 * @property imageCount Number of image files captured for this measurement.
 * @property videoCount Number of video files captured for this measurement.
 * @property filesSize The number of bytes of the files collected for this measurement (log, image and video data).
 */
data class AttachmentMetaData(
    val logCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val filesSize: Long,
) : MetaData, Serializable {
    init {
        require(logCount >= 0) { "Invalid logCount: $logCount" }
        require(imageCount >= 0) { "Invalid imageCount: $imageCount" }
        require(videoCount >= 0) { "Invalid videoCount: $videoCount" }
        require(filesSize >= 0) { "Invalid filesSize: $filesSize" }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.LOG_COUNT.value, logCount)
        ret.put(FormAttributes.IMAGE_COUNT.value, imageCount)
        ret.put(FormAttributes.VIDEO_COUNT.value, videoCount)
        ret.put(FormAttributes.FILES_SIZE.value, filesSize)
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
