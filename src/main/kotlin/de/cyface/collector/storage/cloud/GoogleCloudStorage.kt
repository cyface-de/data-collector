package de.cyface.collector.storage.cloud

import com.google.auth.Credentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.util.UUID

class GoogleCloudStorage internal constructor(
    private val credentials: Credentials,
    private val projectIdentifier: String,
    private val bucketName: String,
    private val uploadIdentifier: UUID
) : CloudStorage {
    /**
     * The Google cloud storage instance, which is only initialized if needed.
     */
    private val storage: Storage by lazy {
        StorageOptions.newBuilder().setCredentials(credentials)
            .setProjectId(projectIdentifier).build().service
    }
    override fun write(bytes: ByteArray) {
        // Google Cloud does not allow to directly append data.
        // Thus we need to write everything to a temporary blob, then merge with the data and delete the temporary one.
        // See for example: https://stackoverflow.com/questions/20876780/how-to-append-write-to-google-cloud-storage-file-from-app-engine
        val tmpBlobInformation = BlobInfo.newBuilder(bucketName, tmpBlobName()).build()
        val dataBlobInformation = BlobInfo.newBuilder(bucketName, dataBlobName()).build()
        val tmpBlob = storage.create(tmpBlobInformation)
        val channel: WritableByteChannel = tmpBlob.writer()
        channel.write(ByteBuffer.wrap(bytes))
        channel.close()

        // Create data if it does not exist
        val dataBlob = storage.get(dataBlobInformation.blobId)
        if (dataBlob == null || !storage.get(dataBlob.blobId).exists()) {
            storage.create(dataBlobInformation)
        }

        // Merge data and tmp
        storage.compose(
            Storage.ComposeRequest.of(
                bucketName,
                listOf(tmpBlobName(), dataBlobName()),
                dataBlobName()
            )
        )
        // Delete the temporary storage
        @Suppress("ForbiddenComment")
        // TODO: This delete does not work at the moment.
        // This is no show stopper as new data just overwrites the old, but it is odd and can increase our storage
        // requirements significantly.
        // Should be fixed prior to putting this into production.
        tmpBlob.delete()
    }

    /**
     * Delete the upload from the cloud storage.
     */
    override fun delete() {
        storage.delete(bucketName, tmpBlobName())
        storage.delete(bucketName, dataBlobName())
    }
    override fun bytesUploaded(): Long {
        val dataBlob = storage[bucketName, dataBlobName()]
        return dataBlob.size
    }
    private fun tmpBlobName(): String {
        return "$uploadIdentifier/tmp"
    }

    private fun dataBlobName(): String {
        return "$uploadIdentifier/data"
    }
}

/*class GoogleCloudStorageBuilder(
        private val credentials: Credentials,
        private val projectIdentifier: String,
        private val bucketName: String,
        private val pagingSize: Long
    ): CloudStorageBuilder {

    override fun createCloudStorage(uploadIdentifier: UUID): CloudStorage {
        return GoogleCloudStorage(credentials, projectIdentifier, bucketName, uploadIdentifier)
    }

    override fun createCleanupOperation(): CleanupOperation {
        val storage = StorageOptions.newBuilder().setCredentials(credentials)
            .setProjectId(projectIdentifier).build().service
        return GoogleCloudCleanupOperation(storage, pagingSize)
    }
}*/
