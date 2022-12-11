package de.cyface.collector.configuration

import com.google.auth.oauth2.GoogleCredentials
import de.cyface.collector.storage.DataStorageServiceBuilder
import de.cyface.collector.storage.cloud.GoogleCloudStorageServiceBuilder
import de.cyface.collector.storage.cloud.MongoDatabase
import io.vertx.core.Vertx
import io.vertx.ext.mongo.MongoClient
import java.io.FileInputStream

data class GoogleCloudStorageType(
    val collectionName: String,
    val projectIdentifier: String,
    val bucketName: String,
    val credentialsFile: String,
    val pagingSize: Long
) : StorageType {
    override fun dataStorageServiceBuilder(vertx: Vertx, mongoClient: MongoClient): DataStorageServiceBuilder {
        val credentials = GoogleCredentials.fromStream(FileInputStream(credentialsFile))
        val dao = MongoDatabase(mongoClient, collectionName)
        return GoogleCloudStorageServiceBuilder(
            credentials,
            projectIdentifier,
            bucketName,
            dao,
            vertx,
            pagingSize
        )
    }
}
