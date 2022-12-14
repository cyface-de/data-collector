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
package de.cyface.collector.storage.cloud

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Disabled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An integration test for storing meta data to an actual running database in the Cloud.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
class MetaDataStorageIT {
    /**
     * The basic test run by this class. The code works more as documentation on how to access an Atlas Cloud Mongo
     * Database. You can run it, but you need to make sure the database to test against actually runs and
     * you need to provide the correct credentials within the [configuration] method.
     */
    @Test
    @Disabled("As this access an actual Mongo Atlas database it should not be run unless really required.")
    fun `Try to Access Atlas Mongo DB and get the number of collections form a test database`() {
        val vertx = Vertx.vertx()

        /* vertx */
        val client = io.vertx.ext.mongo.MongoClient.createWithMongoSettings(
            vertx,
            JsonObject.of("db_name", "test"),
            "source",
            configuration()
        )
        val lock = CountDownLatch(1)
        client.collections
            .onSuccess {
                println("Success: ${it.size}")
                lock.countDown()
            }
            .onFailure {
                println("Fail: $it")
                lock.countDown()
            }

        assertTrue(lock.await(10, TimeUnit.SECONDS))
    }

    /**
     * Provide a working client configuration. Note that you need to change username and password for this to work.
     */
    private fun configuration(): MongoClientSettings {
        val connString = ConnectionString(
            "mongodb+srv://<username>:<password>@cyface.o1bn5yx.mongodb.net/?retryWrites=true&w=majority"
        )
        return MongoClientSettings.builder()
            .applyConnectionString(connString)
            .retryWrites(true)
            .build()
    }
}
