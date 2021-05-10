/*
 * Copyright 20180-2021 Cyface GmbH
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
package de.cyface.apitestutils;

import java.io.IOException;
import java.net.ServerSocket;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.json.JsonObject;

/**
 * A lifecycle handler for a Mongo database you can start and stop on the fly. The database is reinitialized after each
 * restart.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 1.0.0
 */
public final class TestMongoDatabase {
    /**
     * The test Mongo database. This must be shut down after finishing this Mongo database run.
     */
    private MongodProcess mongo;
    /**
     * The executable running the <code>MongodProcess</code>. This must be shut down after finishing this Mongo database
     * run.
     */
    private MongodExecutable mongodExecutable;
    /**
     * The host at which the mongo database is to be started.
     */
    static final String MONGO_HOST = "localhost";
    /**
     * The name of the test Mongo database to create.
     */
    static final String MONGO_DATABASE = "cyface";
    /**
     * The port to run the test Mongo database under.
     */
    private int port;

    /**
     * Sets up the Mongo database used for the test instance.
     *
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    public void start() throws IOException {
        final var socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();
        final var starter = MongodStarter.getDefaultInstance();
        final var mongodConfig = MongodConfig.builder().version(Version.Main.PRODUCTION)
                .net(new Net(MONGO_HOST, port, Network.localhostIsIPv6())).build();
        mongodExecutable = starter.prepare(mongodConfig);
        mongo = mongodExecutable.start();
    }

    /**
     * Provides the database configuration as required by Vertx.
     *
     * @return The database configuration
     */
    public JsonObject config() {
        final JsonObject ret = new JsonObject();
        ret.put("connection_string", "mongodb://" + MONGO_HOST + ":" + port).put("db_name", MONGO_DATABASE);
        return ret;
    }

    /**
     * Stops the test Mongo database after all tests have been finished and waits a little so that a new test may
     * restart the database, without interference.
     */
    public void stop() {
        mongo.stop();
        mongodExecutable.stop();
    }
}
