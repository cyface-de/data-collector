/*
 * Copyright 2018-2024 Cyface GmbH
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
package de.cyface.collector.commons

import io.vertx.core.json.JsonObject
import org.testcontainers.containers.GenericContainer

/**
 * A lifecycle handler for a Mongo database you can start and stop on the fly. The database is reinitialized after each
 * restart.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
class MongoTest {
    private var mongo: GenericContainer<*>? = null

    /**
     * The port to run the test Mongo database under.
     */
    private var internalPort = 0

    /**
     * @return The configuration required to configure a client to the test Mongo server
     */
    fun clientConfiguration(): JsonObject {
        return JsonObject()
            .put("host", getMongoHost())
            .put("port", getMongoPort())
            .put("data_source_name", "cyface-test")
    }

    /**
     * Sets up the Mongo database used for the test instance.
     */
    fun setUpMongoDatabase() {
        this.internalPort = 27017 // Only works with the default internal port
        this.mongo = GenericContainer("mongo:5.0.16").withExposedPorts(internalPort)
        mongo!!.start()
    }

    /**
     * Stops the test Mongo database after all tests have been finished.
     */
    fun stopMongoDb() {
        mongo!!.stop()
        mongo = null
    }

    fun getMongoPort(): Int {
        return mongo!!.getMappedPort(internalPort)
    }

    fun getMongoHost(): String {
        return mongo!!.host
    }
}
