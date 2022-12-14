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
import io.vertx.core.file.FileSystem
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile

/**
 * A [CleanupOperation] for the temporary storage used to write data to GridFS.
 *
 * This deletes all the files where the last updated occured more then `fileExpirationTime` ago.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property fileUploadFolder The path to the folder where all the temporary data for not yet finished uploads is
 * stored.
 * @property fs The Vertx [FileSystem] used to access the temporary storage.
 */
class GridFsCleanupOperation(private val fileUploadFolder: Path, private val fs: FileSystem) : CleanupOperation {

    /**
     * The logger used by objects of this class. Configure it using `src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(GridFsCleanupOperation::class.java)

    override fun clean(fileExpirationTime: Long) {
        // Remove deprecated temp files
        // There is no way to look through all sessions to identify unreferenced files. Thus, we remove files which
        // have not been changed for a long time. The MeasurementHandler handles sessions with "missing" files.
        fs.readDir(fileUploadFolder.absolutePathString()).onSuccess { uploadFiles ->
            uploadFiles.filter { pathname ->
                val path = Paths.get(pathname)
                val notModifiedFor = System.currentTimeMillis() - path.getLastModifiedTime().toMillis()
                path.isRegularFile() && notModifiedFor > fileExpirationTime
            }.forEach { pathname ->
                logger.debug("Cleaning up temp file: {}", pathname)
                fs.delete(pathname).onFailure {
                    logger.warn("Failed to remove temp file: {}", pathname)
                }
            }
        }
    }
}
