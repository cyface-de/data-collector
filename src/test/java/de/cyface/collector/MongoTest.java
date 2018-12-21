/*
 * Copyright 2018 Cyface GmbH
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
package de.cyface.collector;

import java.io.IOException;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * A lifecycle handler for a Mongo database you can start and stop on the fly. The database is reinitialized after each
 * restart.
 * 
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 */
final class MongoTest {
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
     * The port to run the test Mongo database under.
     */
    private int mongoPort;

    /**
     * @return The port to run the test Mongo database under.
     */
    public int getMongoPort() {
        return mongoPort;
    }

    /**
     * Sets up the Mongo database used for the test instance.
     * 
     * @param mongoPort The port to run the test Mongo database under.
     * @throws IOException Fails the test if anything unexpected goes wrong.
     */
    public void setUpMongoDatabase(final int mongoPort) throws IOException {
        this.mongoPort = mongoPort;
        final MongodStarter starter = MongodStarter.getDefaultInstance();
        final IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION)
                .net(new Net("localhost", mongoPort, Network.localhostIsIPv6())).build();
        mongodExecutable = starter.prepare(mongodConfig);
        mongo = mongodExecutable.start();
    }

    /**
     * Stops the test Mongo database after all tests have been finished and waits a little so that a new test may
     * restart the database, without intererence.
     */
    public void stopMongoDb() {
        mongo.stop();
        mongodExecutable.stop();
    }
}
