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
 * A lifecycle handler for a Mongo database you can start and stop on the fly.
 * The database is reinitialized after each restart.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
class MongoTest {
	/**
	 * The port of the API endpoint providing access to the test Mongo database
	 * instance.
	 */
	static final int MONGO_PORT = 12345;
	/**
	 * The test Mongo database. This must be shut down after finishing this Mongo
	 * database run.
	 */
	private MongodProcess mongo;
	/**
	 * The executable running the <code>MongodProcess</code>. This must be shut down
	 * after finishing this Mongo database run.
	 */
	private MongodExecutable mongodExecutable;

	/**
	 * Sets up the Mongo database used for the test instance.
	 * 
	 * @throws IOException Fails the test if anything unexpected goes wrong.
	 */
	public void setUpMongoDatabase() throws IOException {
		MongodStarter starter = MongodStarter.getDefaultInstance();
		IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION)
				.net(new Net("localhost", MONGO_PORT, Network.localhostIsIPv6())).build();
		mongodExecutable = starter.prepare(mongodConfig);
		mongo = mongodExecutable.start();
	}

	/**
	 * Stops the test Mongo database after all tests have been finished.
	 */
	public void stopMongoDb() {
		mongo.stop();
		mongodExecutable.stop();
	}
}
