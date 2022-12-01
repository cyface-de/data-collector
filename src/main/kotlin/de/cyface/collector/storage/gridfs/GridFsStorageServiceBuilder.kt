/*
 * Copyright 2022 Cyface GmbH
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

/**
 * A builder creating [GridFsStorageService] and [GridFsCleanupOperation] instances.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property dao The data access object to access Mongo Grid FS.
 * @property fileSystem The Vertx [FileSystem] used to store temporary data of not yet finished uploads.
 * @property uploadFolder The path to the folder for the temporary data on the `fileSystem`.
 */
class GridFsStorageServiceBuilder(
    private val dao: GridFsDao,
    private val fileSystem: FileSystem,
    private val uploadFolder: Path
) : DataStorageServiceBuilder {

    /**
     * The logger used for objects of this class. Configure it using `src/main/resources/logback.xml`.
     */
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

    /**
     * Called after the existence of the upload folder was checked.
     *
     * @param exists `true` if the upload folder did exist; `false` otherwise.
     * @return A [Future] that is notified of the success or failure of this operation, upon completion.
     */
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
