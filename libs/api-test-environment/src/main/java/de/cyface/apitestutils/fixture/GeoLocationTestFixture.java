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

import org.apache.commons.lang3.Validate;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
     * The identifier of the measurement used during the test.
     */
    private final Long measurementIdentifier;
    /**
     * The world wide unique identifier of the device used during the test.
     */
    private final String deviceIdentifier;
    /**
     * under which the data should be added. Choose {@link #TEST_GROUP_USER_USERNAME} if the data of all group's users
     * is to be accessed. Choose {@link #TEST_USER_NAME} if the data should be added to the user which is used in
     * authentication. Useful if the data of the currently logged in user should be accessed.
     */
    private final String dataOwnerUsername;

    /**
     * Creates a new completely initialized fixture for the test.
     *
     * @param measurementIdentifier The identifier of the measurement used during the test
     * @param deviceIdentifier The world wide unique identifier of the device used during the test
     * @param dataOwnerUsername under which the data should be added. Choose {@link #TEST_GROUP_USER_USERNAME} if the
     *            data of all
     *            group's users is to be accessed. Choose {@link #TEST_USER_NAME} if the data should be added to the
     *            user which is used in authentication. Useful if the data of the currently logged in user should be
     *            accessed.
     */
    @SuppressWarnings("unused") // API
    public GeoLocationTestFixture(final Long measurementIdentifier, final String deviceIdentifier,
                                  final String dataOwnerUsername) {
        Validate.notNull(measurementIdentifier);
        Validate.notEmpty(deviceIdentifier);

        this.measurementIdentifier = measurementIdentifier;
        this.deviceIdentifier = deviceIdentifier;
        this.dataOwnerUsername = dataOwnerUsername;
    }

    @Override
    public void insertTestData(MongoClient mongoClient, Handler<AsyncResult<Void>> insertCompleteHandler) {
        final Promise<Void> createAuthUserPromise = Promise.promise();
        final Promise<Void> createTestUserPromise = Promise.promise();
        final Promise<Void> createMeasurementTestDocumentPromise = Promise.promise();
        final CompositeFuture synchronizer = CompositeFuture.all(createAuthUserPromise.future(),
                createTestUserPromise.future(), createMeasurementTestDocumentPromise.future());
        synchronizer.onComplete(result -> {
            if (result.succeeded()) {
                insertCompleteHandler.handle(Future.succeededFuture());
            } else {
                insertCompleteHandler.handle(Future.failedFuture(result.cause()));
            }
        });

        new TestUser(TEST_USER_NAME, "secret", TEST_GROUP + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX).insert(
                mongoClient, createAuthUserPromise);
        new TestUser(TEST_GROUP_USER_USERNAME, "secret", TEST_GROUP + DatabaseConstants.USER_GROUP_ROLE_SUFFIX)
                .insert(mongoClient, createTestUserPromise);

        new TestMeasurementDocument(dataOwnerUsername,
                measurementIdentifier, deviceIdentifier).insert(mongoClient,
                        createMeasurementTestDocumentPromise);
    }
}
