package de.cyface.collector.storage.cloud

interface CloudStorage {
    fun write(bytes: ByteArray)
    fun delete()
    fun bytesUploaded(): Long
}
