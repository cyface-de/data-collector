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

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.mongo.MongoClient;

/**
 * A fixture providing data to use for testing the raw geo location export.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.0.0
 */
@SuppressWarnings("unused") // API
public final class GeoLocationTestFixture implements TestFixture {
    /**
     * The name of the test group to export test data of.
     */
    private static final String TEST_GROUP = "testGroup";
    /**
     * The name of the user to add test data for in group-data accessing tests.
     */
    public static final String TEST_GROUP_USER_USERNAME = TEST_GROUP + "1";
    /**
     * The user which is used for authentication in the test.
     */
    public static final String TEST_USER_NAME = "admin";
    /**
     * The measurements used during the test
     */
    private final List<TestMeasurementDocument> testMeasurementDocuments;

    /**
     * Creates a new completely initialized fixture for the test.
     *
     * @param testMeasurementDocuments The measurements used during the test
     */
    @SuppressWarnings("unused") // API
    public GeoLocationTestFixture(final List<TestMeasurementDocument> testMeasurementDocuments) {
        this.testMeasurementDocuments = testMeasurementDocuments;
    }

    @Override
    public Future<Void> insertTestData(MongoClient mongoClient) {
        final Promise<Void> createAuthUserPromise = Promise.promise();
        final Promise<Void> createTestUserPromise = Promise.promise();
        final var futures = new ArrayList<Future>();
        futures.add(createAuthUserPromise.future());
        futures.add(createTestUserPromise.future());
        testMeasurementDocuments.forEach(document -> {
            final Promise<Void> promise = Promise.promise();
            futures.add(promise.future());
            document.insert(mongoClient, promise);
        });
        new TestUser(TEST_USER_NAME, "secret", TEST_GROUP + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX).insert(
                mongoClient, createAuthUserPromise);
        new TestUser(TEST_GROUP_USER_USERNAME, "secret", TEST_GROUP + DatabaseConstants.USER_GROUP_ROLE_SUFFIX)
                .insert(mongoClient, createTestUserPromise);
        final var composition = CompositeFuture.all(futures);
        final Promise<Void> promise = Promise.promise();
        composition.onSuccess(succeeded -> promise.complete()).onFailure(promise::fail);
        return promise.future();
    }
}
