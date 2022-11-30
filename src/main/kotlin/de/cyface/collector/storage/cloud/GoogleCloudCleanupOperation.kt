package de.cyface.collector.storage.cloud

import com.google.cloud.storage.Storage
import de.cyface.collector.storage.CleanupOperation
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class GoogleCloudCleanupOperation(
    private val storage: Storage,
    private val pagingSize: Long
) : CleanupOperation {

    private val nameExtraction = "(.+)\\.tmp".toRegex()

    override fun clean(fileExpirationTime: Long) {
        val currentTime = OffsetDateTime.now()
        val storedFiles = storage.list(Storage.BucketListOption.pageSize(pagingSize))
        val expirationTime = fileExpirationTime * NANO_SECONDS_IN_A_MILLISECOND
        storedFiles.iterateAll().forEach { bucket ->

            val updateTime = bucket.updateTimeOffsetDateTime
            val expiredTimeSinceLastUpdate = currentTime.minus(
                updateTime.toEpochSecond() * MILLI_SECONDS_IN_A_SECOND,
                ChronoUnit.MILLIS
            )
            val expiredTimeInNanos = expiredTimeSinceLastUpdate.nano

            if (expiredTimeInNanos < expirationTime) {
                return@forEach
            }

            if (bucket.name.endsWith(".tmp")) {
                val uploadIdentifier = nameExtraction.matchEntire(bucket.name)?.groups?.get(1)?.value ?: return@forEach

                val dataName = "$uploadIdentifier.data"

                storage.delete(bucket.name)
                storage.delete(dataName)
            }
        }
    }

    companion object {
        const val NANO_SECONDS_IN_A_MILLISECOND = 1_000_000L
        const val MILLI_SECONDS_IN_A_SECOND = 1_000L
    }
}
