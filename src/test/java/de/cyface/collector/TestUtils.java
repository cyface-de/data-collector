/*
 * Copyright 2018-2021 Cyface GmbH
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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Class providing static utility functionality used by the whole test suite.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 2.0.0
 */
public final class TestUtils {
    /**
     * Private constructor to avoid instantiation of static utility class.
     */
    private TestUtils() {
        // Nothing to do here.
    }

    /**
     * Utility method used to get a new JWT token from the server.
     *
     * @param client The client to use to access the server.
     * @param handler <code>Handler</code> called when the response has returned.
     * @param port The port running the test server on localhost to authenticate against.
     * @param endpoint The API endpoint to authenticate against, e.g. `/api/v3/`
     */
    public static void authenticate(final WebClient client, final int port, final String endpoint,
                                    final Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        final JsonObject body = new JsonObject();
        body.put("username", "admin");
        body.put("password", "secret");

        client.post(port, "localhost", endpoint).sendJsonObject(body, handler);
    }
}
