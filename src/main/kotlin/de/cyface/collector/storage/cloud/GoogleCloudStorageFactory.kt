package de.cyface.collector.storage.cloud

import com.google.auth.Credentials
import java.util.UUID

/**
 * An implementation of a `CloudStorageFactory` for accessing Google Cloud Storage.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property credentials The Google Cloud [Credentials] used to authenticate with Google Cloud Storage.
 * For information on how to acquire such an instance see the [Google Cloud documentation]
 * (https://github.com/googleapis/google-auth-library-java/blob/040acefec507f419f6e4ec4eab9645a6e3888a15/samples/snippets/src/main/java/AuthenticateExplicit.java).
 * @property projectIdentifier The Google Cloud project identifier used by this service.
 * @property bucketName The Google Cloud Storage bucket name used to store data to.
 */
@Suppress("MaxLineLength")
class GoogleCloudStorageFactory(
    private val credentials: Credentials,
    private val projectIdentifier: String,
    private val bucketName: String
) : CloudStorageFactory {
    /**
     * Create the actual storage instance used to communicate with the Google Cloud.
     */
    override fun create(uploadIdentifier: UUID): CloudStorage {
        return GoogleCloudStorage(credentials, projectIdentifier, bucketName, uploadIdentifier)
    }
}
