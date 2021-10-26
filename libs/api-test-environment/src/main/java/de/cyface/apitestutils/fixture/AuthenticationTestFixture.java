/*
 * Copyright 2020-2021 Cyface GmbH
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
package de.cyface.apitestutils.fixture;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.mongo.MongoClient;

/**
 * The test data used to test authentication requests to a Cyface exporter server.
 * It provides users to authenticate with. Currently this is a user "admin" with a password "secret" and the manager
 * role.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
@SuppressWarnings("unused") // API
public final class AuthenticationTestFixture implements TestFixture {

    @Override
    public Future<Void> insertTestData(MongoClient mongoClient) {

        final Promise<Void> promise = Promise.promise();
        final var testUser = new TestUser("admin", "secret",
                "testGroup" + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX);
        testUser.insert(mongoClient, result -> {
            if (result.succeeded()) {
                promise.complete();
            } else {
                promise.fail(result.cause());
            }
        });
        return promise.future();
    }
}
