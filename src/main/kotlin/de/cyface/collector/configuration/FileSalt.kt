/*
 * Copyright 2022 Cyface GmbH
 *
 * This file is part of the Cyface Data Collector.
 *
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
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
 * @property sourcePath The [Path] to the file containing the authentication salt.
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
