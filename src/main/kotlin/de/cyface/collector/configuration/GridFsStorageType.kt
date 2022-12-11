package de.cyface.collector.configuration

import de.cyface.collector.storage.DataStorageServiceBuilder
import de.cyface.collector.storage.gridfs.GridFsDao
import de.cyface.collector.storage.gridfs.GridFsStorageServiceBuilder
import io.vertx.core.Vertx
import io.vertx.ext.mongo.MongoClient
import org.apache.commons.lang3.Validate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

data class GridFsStorageType(val uploadFolder: Path) : StorageType {
    override fun dataStorageServiceBuilder(vertx: Vertx, mongoClient: MongoClient): DataStorageServiceBuilder {
        if (!uploadFolder.exists()) {
            val createdDirectory = Files.createDirectory(uploadFolder)
            Validate.isTrue(createdDirectory.exists())
        }
        val fileSystem = vertx.fileSystem()
        val dao = GridFsDao(mongoClient)
        return GridFsStorageServiceBuilder(dao, fileSystem, uploadFolder)
    }
}
