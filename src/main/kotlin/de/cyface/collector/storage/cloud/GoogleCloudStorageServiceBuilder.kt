package de.cyface.collector.storage.cloud

import com.google.auth.Credentials
import com.google.cloud.storage.StorageOptions
import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.DataStorageServiceBuilder
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx

class GoogleCloudStorageServiceBuilder(
    private val credentials: Credentials,
    private val projectIdentifier: String,
    private val bucketName: String,
    private val dao: Database,
    private val vertx: Vertx,
    private val pagingSize: Long
) : DataStorageServiceBuilder {
    override fun create(): Future<DataStorageService> {
        val ret = Promise.promise<DataStorageService>()
        vertx.runOnContext {
            ret.complete(GoogleCloudStorageService(dao, vertx, credentials, projectIdentifier, bucketName))
        }
        return ret.future()
    }

    override fun createCleanupOperation(): CleanupOperation {
        val storage = StorageOptions.newBuilder().setCredentials(credentials)
            .setProjectId(projectIdentifier).build().service
        return GoogleCloudCleanupOperation(storage, pagingSize)
    }
}
