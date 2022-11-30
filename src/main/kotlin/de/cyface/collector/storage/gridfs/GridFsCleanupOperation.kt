package de.cyface.collector.storage.gridfs

import de.cyface.collector.storage.CleanupOperation
import io.vertx.core.file.FileSystem
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile

class GridFsCleanupOperation(private val fileUploadFolder: Path, private val fs: FileSystem) : CleanupOperation {

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
