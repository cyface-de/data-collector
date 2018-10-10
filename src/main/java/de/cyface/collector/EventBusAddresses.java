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

/**
 * Utility class containing all the valid Vertx event bus addresses used in the
 * Cyface data collector.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class EventBusAddresses {

	/**
	 * Address used to inform the event bus about a new measurement, which has
	 * arrived.
	 */
	public static final String NEW_MEASUREMENT = "de.cyface.collector.measurement_new";
	/**
	 * Address used to inform the event bus about the successful storage of a
	 * measurement.
	 */
	public static final String MEASUREMENT_SAVED = "de.cyface.collector.measurement_saved";

	/**
	 * Private constructor to prevent instantiation of utility classes.
	 */
	private EventBusAddresses() {
		// Nothing to do here.
	}
}
