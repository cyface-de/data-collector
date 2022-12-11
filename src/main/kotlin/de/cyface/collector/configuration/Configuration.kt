package de.cyface.collector.configuration

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get
import org.apache.commons.lang3.Validate
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A POJO representing the configuration provided to this application.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
data class Configuration(
    val jwtPrivate: Path,
    val jwtPublic: Path,
    val serviceHttpAddress: URL,
    val mongoDb: JsonObject,
    val adminUser: String,
    val adminPassword: String,
    val salt: Salt,
    val jwtExpiration: Int,
    val uploadExpiration: Long,
    val measurementPayloadLimit: Long,
    val managementHttpAddress: URL,
    val metricsEnabled: Boolean,
    val storageType: StorageType
) {
    companion object {
        fun deserialize(json: JsonObject): Future<Configuration> {
            val result = Promise.promise<Configuration>()

            try {
                val jwtPrivate = Paths.get(json.get<String>("jwt.private"))
                val jwtPublic = Paths.get(json.get<String>("jwt.public"))
                val httpHost = json.get<String>("http.host")
                val httpPort = json.get<Int>("http.port")
                val httpEndpoint = httpEndpoint(json["http.endpoint"])
                val serviceHttpAddress = URL("https", httpHost, httpPort, httpEndpoint)
                val mongoDb = json.get<JsonObject>("mongo.db")
                val adminUser = json.get<String>("admin.user")
                val adminPassword = json.get<String>("admin.password")
                val jwtExpiration = json.get<Int>("jwt.expiration")
                val uploadExpiration = json.get<Long>("upload.expiration")
                val measurementPayloadLimit = json.get<Long>("measurement.payload.limit")
                val httpPortManagement = json.get<Int>("http.port.management")
                val managementHttpAddress = URL("https", httpHost, httpPortManagement, "/")
                val metricsEnabled = json.get<Boolean>("metrics.enabled")
                val storageTypeJson = json.get<JsonObject>("storage-type")
                val storageType = storageType(storageTypeJson)

                val saltCall = salt(json)
                saltCall.onSuccess { salt ->
                    result.complete(
                        Configuration(
                            jwtPrivate,
                            jwtPublic,
                            serviceHttpAddress,
                            mongoDb,
                            adminUser,
                            adminPassword,
                            salt,
                            jwtExpiration,
                            uploadExpiration,
                            measurementPayloadLimit,
                            managementHttpAddress,
                            metricsEnabled,
                            storageType
                        )
                    )
                }
                saltCall.onFailure(result::fail)
            } catch (e: NullPointerException) {
                throw InvalidConfig(
                    "Unable to load configuration. Some parameters are missing. " +
                        "Please refer to the documentation or an example file.",
                    e
                )
            }

            return result.future()
        }

        private fun salt(json: JsonObject): Future<Salt> {
            val ret = Promise.promise<Salt>()
            if (json.containsKey("salt") && json.containsKey("salt.path")) {
                throw InvalidConfig(
                    "Please provide either salt or salt.path parameter. " +
                        "Using both is not permitted."
                )
            }
            if (json.containsKey("salt")) {
                ret.complete(ValueSalt(json.get("salt")))
            } else if (json.containsKey("salt.path")) {
                ret.complete(FileSalt(Path.of(json.get<String>("salt.path"))))
            } else {
                throw InvalidConfig(
                    "Please provide a valid salt either as a file with the salt.path parameter " +
                        "or as a value via the salt parameter."
                )
            }
            return ret.future()
        }

        private fun storageType(storageTypeConfig: JsonObject): StorageType {
            when (val storageTypeString = storageTypeConfig.getString("type")) {
                "gridfs" -> {
                    val uploadFolder = Path.of(storageTypeConfig.getString("uploads-folder", "file-uploads/"))
                    return GridFsStorageType(uploadFolder)
                }

                "google" -> {
                    val collectionName = storageTypeConfig.get<String>("collection-name")
                    val projectIdentifier = storageTypeConfig.get<String>("project-identifier")
                    val bucketName = storageTypeConfig.get<String>("bucket-name")
                    val credentialsFile = storageTypeConfig.get<String>("credentials-file")
                    val pagingSize = storageTypeConfig.get<Long>("paging-size")
                    return GoogleCloudStorageType(
                        collectionName,
                        projectIdentifier,
                        bucketName,
                        credentialsFile,
                        pagingSize
                    )
                }
                null -> throw InvalidConfig(
                    "Storage type configuration missing. " +
                        "Please provide either a Google or GridFS Storage type."
                )

                else -> throw InvalidConfig("Invalid storage type $storageTypeString!")
            }
        }

        private fun httpEndpoint(endpoint: String): String {
            Validate.notEmpty(
                endpoint,
                "Endpoint not found. Please use the parameter $endpoint."
            )
            val builder = StringBuilder(endpoint)
            val lastChar = endpoint.last()
            if (lastChar == '/') {
                builder.append("*")
            } else if (lastChar != '*') {
                builder.append("/*")
            }
            return builder.toString()
        }
    }
}
