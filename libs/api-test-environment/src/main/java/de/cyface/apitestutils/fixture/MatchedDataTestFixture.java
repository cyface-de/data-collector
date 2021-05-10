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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Inserts map matched test data into a test Mongo database instance.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public final class MatchedDataTestFixture implements TestFixture {
    /**
     * The name of the collection in the Mongo database containing the map matched data
     */
    private static final String MONGO_COLLECTION_NAME = "map-matched";
    /**
     * The user which is used for authentication in the test.
     */
    private static final String TEST_USER_NAME = "admin";
    /**
     * The name of the test group with access permission to the test data.
     */
    private final String testGroup;

    /**
     * Creates a new completely initialized fixture for map matched data.
     *
     * @param testGroup The name of the test group with access permission to the test data
     */
    public MatchedDataTestFixture(final String testGroup) {
        Validate.notEmpty(testGroup);

        this.testGroup = testGroup;
    }

    @Override
    public void insertTestData(MongoClient mongoClient, Handler<AsyncResult<Void>> insertCompleteHandler) {
        Promise<String> measurementInserted = Promise.promise();
        Promise<Void> testUserInserted = Promise.promise();
        var insertionFinished = CompositeFuture.all(measurementInserted.future(),
                testUserInserted.future());
        insertionFinished.onComplete(insertionResult -> {
            if (insertionResult.succeeded()) {
                mongoClient.find(MONGO_COLLECTION_NAME, new JsonObject(), event -> {
                    final var findResult = event.result();
                    System.out.println(findResult);

                    insertCompleteHandler.handle(Future.succeededFuture());
                });
            } else {
                insertCompleteHandler.handle(Future.failedFuture(insertionResult.cause()));
            }
        });
        final var testUser = new TestUser(TEST_USER_NAME, "secret",
                testGroup + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX,
                testGroup + DatabaseConstants.USER_GROUP_ROLE_SUFFIX);
        testUser.insert(mongoClient, testUserInserted);
        final var measurement = new JsonObject();
        measurement.put("device-identifier", "10ea2071-470e-4418-a016-34cd32060810");
        measurement.put("measurement-identifier", 2);
        measurement.put("username", TEST_USER_NAME);
        measurement.put("matched-points", matchedPoints());
        mongoClient.insert(MONGO_COLLECTION_NAME, measurement, measurementInserted);
    }

    /**
     * @return A fixture with a test array of matched points
     */
    private JsonArray matchedPoints() {
        var ret = new JsonArray();

        ret.add(matchedPoint(51.052111, 13.728918, 1564646168782L, 1001, 1854503083L, 51.0521917, 13.728969,
                2276890449L, 51.0520856, 13.7289019));
        ret.add(matchedPoint(51.052111, 13.728918, 1564646169815L, 1002, 1854503083L, 51.0521917, 13.728969,
                2276890449L, 51.0520856, 13.7289019));
        ret.add(matchedPoint(51.052111, 13.728918, 1564646170815L, 1003, 1854503083, 51.0521917, 13.728969, 2276890449L,
                51.0520856, 13.7289019));

        return ret;
    }

    /**
     * Create a single matched point in JSON notation, which can be used as part of a test fixture.
     *
     * @param latitude The geographical latitude of the point
     * @param longitude The geographical longitude of the point
     * @param timestamp The time of the point measurement in milliseconds since the 01.01.1970
     * @param wayId the OSM identifier of the way this point is map-matched to
     * @param predecessorIdentifier The OSM node identifier of the predecessor of the matched point
     * @param predecessorLatitude The geographical latitude of the predecessor node
     * @param predecessorLongitude The geographical longitude of the predecessor node
     * @param successorIdentifier The OSM node identifier of the successor of the matched point
     * @param successorLatitude The geographical latitude of the successor node
     * @param successorLongitude The geographical longitude of the successor node
     * @return A matched point in JSON format
     */
    @SuppressWarnings("SameParameterValue") // To be adjustable for later tests
    private JsonObject matchedPoint(final double latitude, final double longitude,
            final long timestamp, final long wayId, final long predecessorIdentifier, final double predecessorLatitude,
            final double predecessorLongitude, final long successorIdentifier, final double successorLatitude,
            final double successorLongitude) {
        final var ret = new JsonObject();

        ret.put("time", timestamp);
        ret.put("wayId", new JsonArray().add(wayId));
        final var coordinates = new JsonArray().add(longitude).add(latitude);
        ret.put("geometry", new JsonObject().put("type", "Point").put("coordinates", coordinates));
        final var predecessorGeometry = new JsonObject().put("type", "Point").put("coordinates",
                new JsonArray().add(predecessorLongitude).add(predecessorLatitude));
        ret.put("predecessor",
                new JsonObject().put("osm-id", predecessorIdentifier).put("geometry", predecessorGeometry));
        final var successorGeometry = new JsonObject().put("type", "Point").put("coordinates",
                new JsonArray().add(successorLongitude).add(successorLatitude));
        ret.put("successor", new JsonObject().put("osm-id", successorIdentifier).put("geometry", successorGeometry));

        return ret;
    }
}
