/*
 * Copyright 2018 Cyface GmbH
 * 
 * This file is part of the Cyface Data Collector.
 *
 *  The Cyface Data Collector is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  The Cyface Data Collector is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with the Cyface Data Collector.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * An enumeration of parameters, that may be provided upon application startup,
 * to configure the application.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public enum Parameter {
	/**
	 * The location of the keystore used to sign JWT tokens.
	 */
	JWT_KEYSTORE("keystore.jwt"),
	/**
	 * The server port the API shall be available at.
	 */
	HTTP_PORT("http.port"),
	/**
	 * The location of the keystore for HTTPS connections.
	 */
	TLS_KEYSTORE("keystore.tls"),
	/**
	 * Detailed connection information about the Mongo user database. This database
	 * stores all the credentials of users capable of logging in to the systems.
	 * This should be a JSON object with supported parameters explained at: <a href=
	 * "https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client">https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client</a>.
	 */
	MONGO_USER_DB("mongo.userdb"),
	/**
	 * Detailed connection information about the Mongo data database. This database
	 * stores all data received via the REST-API. This should be a JSON object with
	 * supported parameters explained at: <a href=
	 * "https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client">https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client</a>.
	 */
	MONGO_DATA_DB("mongo.datadb");

	/**
	 * The logger used for objects of this class. You can change its configuration
	 * by changing the values in
	 * <code>src/main/resources/vertx-default-jul-logging.properties</code>.
	 */
	private final static Logger LOGGER = LoggerFactory.getLogger(Parameter.class);

	/**
	 * The parameter key used to load its value from the JSON configuration.
	 */
	private final String key;

	/**
	 * Creates a new completely initialized parameter.
	 * 
	 * @param key The parameter key used to load its value from the JSON
	 *            configuration.
	 */
	private Parameter(final String key) {
		this.key = key;
	}

	/**
	 * @return The parameter key used to load its value from the JSON configuration.
	 */
	public String key() {
		return key;
	}

	/**
	 * Provides the string value of this parameter from the Vert.x configuration or
	 * the <code>defaultValue</code> if there was none.
	 * 
	 * @param vertx        The <code>Vertx</code> instance containing the
	 *                     configuration.
	 * @param defaultValue A default value if the parameter was missing.
	 * @return Either the value of the parameter as a <code>String</code> or the
	 *         <code>defaultValue</code>.
	 */
	public String stringValue(final Vertx vertx, final String defaultValue) {
		final String ret = vertx.getOrCreateContext().config().getString(key);
		return ret == null ? defaultValue : ret;
	}

	/**
	 * Provides the integer value of this parameter from the Vert.x configuration or
	 * the <code>defaultValue</code> if there was none.
	 * 
	 * @param vertx        The <code>Vertx</code> instance containing the
	 *                     configuration.
	 * @param defaultValue A default value if the parameter was missing.
	 * @return Either the value of the parameter as an <code>int</code> or the
	 *         <code>defaultValue</code>.
	 * @throws ClassCastException If the value was not an integer.
	 */
	public int intValue(final Vertx vertx, final int defaultValue) {
		final Integer ret = vertx.getOrCreateContext().config().getInteger(key);
		return ret == null ? defaultValue : ret;
	}

	/**
	 * Provides the JSON value of this parameter from the Vert.x configuration or
	 * the <code>defaultValue</code> if there was none.
	 * 
	 * @param vertx        The <code>Vertx</code> instance containing the
	 *                     configuration.
	 * @param defaultValue A default value if the parameter was missing.
	 * @return Either the value of the parameter as a JSON object or the
	 *         <code>defaultValue</code>.
	 */
	public JsonObject jsonValue(final Vertx vertx, JsonObject defaultValue) {
		final JsonObject ret = vertx.getOrCreateContext().config().getJsonObject(key);
		LOGGER.info("Read json value " + ret + " for key " + key);
		return ret == null ? defaultValue : ret;
	}
}
