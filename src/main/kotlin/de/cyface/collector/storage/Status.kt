package de.cyface.collector.storage

import java.util.UUID

data class Status(val uploadIdentifier: UUID, val type: StatusType, val byteSize: Long)

enum class StatusType {
    COMPLETE,
    INCOMPLETE
}