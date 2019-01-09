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
/**
 * This package contains the Vert.x Verticles steering the Cyface data collector. There is a <code>MainVerticle</code>
 * as a central entrypoint. It deploys all the other necessary Verticles.
 * <p>
 * The {@link de.cyface.collector.verticle.CollectorApiVerticle} starts the collector API itself, while the
 * {@link de.cyface.collector.verticle.ManagementApiVerticle} starts the management console on a different server using
 * a different port.
 * <p>
 * The {@link de.cyface.collector.verticle.SerializationVerticle} is a worker, that is responsible to store arriving
 * measurements to the Mongo database.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
package de.cyface.collector.verticle;