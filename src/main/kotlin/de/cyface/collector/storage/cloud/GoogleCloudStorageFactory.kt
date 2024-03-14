/*
 * Copyright 2024 Cyface GmbH
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
package de.cyface.collector.storage.cloud

import com.google.auth.Credentials
import java.util.UUID

/**
 * An implementation of a `CloudStorageFactory` for accessing Google Cloud Storage.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 7.1.1
 * @property credentials The Google Cloud [Credentials] used to authenticate with Google Cloud Storage.
 * For information on how to acquire such an instance see the [Google Cloud documentation]
 * (https://github.com/googleapis/google-auth-library-java/blob/040acefec507f419f6e4ec4eab9645a6e3888a15/samples/snippets/src/main/java/AuthenticateExplicit.java).
 * @property projectIdentifier The Google Cloud project identifier used by this service.
 * @property bucketName The Google Cloud Storage bucket name used to store data to.
 */
@Suppress("MaxLineLength")
class GoogleCloudStorageFactory(
    private val credentials: Credentials,
    private val projectIdentifier: String,
    private val bucketName: String
) : CloudStorageFactory {
    /**
     * Create the actual storage instance used to communicate with the Google Cloud.
     */
    override fun create(uploadIdentifier: UUID): CloudStorage {
        return GoogleCloudStorage(credentials, projectIdentifier, bucketName, uploadIdentifier)
    }
}
