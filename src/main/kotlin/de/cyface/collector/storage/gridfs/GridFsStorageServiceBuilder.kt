package de.cyface.collector.storage.gridfs

import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.DataStorageServiceBuilder
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.file.FileSystem
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class GridFsStorageServiceBuilder(
    private val dao: GridFsDao,
    private val fileSystem: FileSystem,
    private val uploadFolder: Path
) : DataStorageServiceBuilder {

    val logger = LoggerFactory.getLogger(DataStorageServiceBuilder::class.java)

    override fun create(): Future<DataStorageService> {
        logger.info("Creating data storage connection!")
        val ret = Promise.promise<DataStorageService>()

        val indexCreationCall = dao.createIndices()
        val uploadFolderExistsCall = fileSystem.exists(uploadFolder.absolutePathString())
        val uploadFolderCreationCall = uploadFolderExistsCall.compose { exists ->
            onUploadFolderChecked(exists)
        }

        val initialSetup = CompositeFuture.all(uploadFolderCreationCall, indexCreationCall)
        initialSetup.onSuccess {
            logger.info("Successfully connected to data storage!")
            ret.complete(GridFsStorageService(dao, fileSystem, uploadFolder))
        }
        initialSetup.onFailure { cause ->
            logger.error("Failed to setup connection to data storage!", cause)
            ret.fail(cause)
        }

        return ret.future()
    }

    override fun createCleanupOperation(): CleanupOperation {
        return GridFsCleanupOperation(uploadFolder, fileSystem)
    }

    private fun onUploadFolderChecked(exists: Boolean): Future<Void> {
        return if (!exists) {
            logger.info("Upload folder did not exist. Trying to create!")
            fileSystem.mkdir(uploadFolder.absolutePathString())
        } else {
            logger.info("Upload folder exists. Skipping creation!")
            Future.succeededFuture()
        }
    }
}
