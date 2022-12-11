package de.cyface.collector.configuration

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * An encryption salt loaded from a file.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
data class FileSalt(val sourcePath: Path) : Salt {
    override fun bytes(vertx: Vertx): Future<ByteArray> {
        val ret = Promise.promise<ByteArray>()
        val fileSystem = vertx.fileSystem()
        fileSystem.readFile(
            sourcePath.absolutePathString()
        ) { readFileResult: AsyncResult<Buffer> ->
            if (readFileResult.failed()) {
                ret.fail(readFileResult.cause())
            } else {
                val loadedSalt =
                    readFileResult.result().bytes
                ret.complete(loadedSalt)
            }
        }
        return ret.future()
    }
}
