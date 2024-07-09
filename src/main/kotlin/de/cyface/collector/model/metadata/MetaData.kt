package de.cyface.collector.model.metadata

import io.vertx.core.json.JsonObject

interface MetaData {
    fun toJson(): JsonObject

    companion object {
        /**
         * Maximum size of a metadata field, with plenty space for future development. Prevents attackers from putting
         * arbitrary long data into these fields.
         */
        const val MAX_GENERIC_METADATA_FIELD_LENGTH = 30
    }
}
