package de.cyface.collector.configuration

import io.vertx.core.Future
import io.vertx.core.Vertx

/**
 * A very simple salt represented by its value.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
data class ValueSalt(val value: String) : Salt {
    override fun bytes(vertx: Vertx): Future<ByteArray> {
        return Future.succeededFuture(value.toByteArray())
    }
}
