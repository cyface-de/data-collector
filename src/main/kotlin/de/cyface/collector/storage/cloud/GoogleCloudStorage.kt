/*
 * Copyright 2022-2024 Cyface GmbH
 *
 * This file is part of the Serialization.
 *
 * The Serialization is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Serialization is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Serialization. If not, see <http://www.gnu.org/licenses/>.
 */
@file:Suppress("AnnotationSpacing")

package de.cyface.collector.storage.cloud

import com.google.auth.Credentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.util.UUID

@Suppress("MaxLineLength")
/**
 * A [CloudStorage] implementation for the Google Cloud.
 *
 * This class encapsulates all the infomration required to build the [Storage] instance for a single upload together
 * with the necessary methods to work on that upload.
 *
 * Repeatable uploads on Google Cloud are little tricky.
 * BLOBs on Cloud Storage are immutable.
 * However, it is possible to concatenate two BLOBs into a new one.
 * Therefore, this implementation stores new uploads into a temporary file.
 * After a chunk was uploaded, the temporary data and the already present data are concatenated, overwriting the
 * previously present data.
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 * @property credentials A Google Cloud credentials instance. For information on how to acquire such an instance see
 * the [Google Cloud documentation](https://github.com/googleapis/google-auth-library-java/blob/040acefec507f419f6e4ec4eab9645a6e3888a15/samples/snippets/src/main/java/AuthenticateExplicit.java).
 * @property projectIdentifier The name of the Google Cloud project to upload the data to.
 * @property bucketName The name of the Cloud Storage bucket to upload the data to.
 * @property uploadIdentifier The identifier of the upload this storage is created for.
 */
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
        storage.delete(bucketName, tmpBlobName())
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

    fun download(): ByteArrayOutputStream {
        val dataMetaInformation = BlobInfo.newBuilder(bucketName, dataBlobName()).build()

        val output = ByteArrayOutputStream()
        storage.downloadTo(dataMetaInformation.blobId, output)

        return output
    }

    /**
     * The name of the BLOB storing the temporary data before it is merged into permanent storage.
     */
    private fun tmpBlobName(): String {
        return "$uploadIdentifier/tmp"
    }

    /**
     * The already permanently stored data BLOB.
     */
    private fun dataBlobName(): String {
        return "$uploadIdentifier/data"
    }
}
