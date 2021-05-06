/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils.fixture;

import de.cyface.apitestutils.TestEnvironment;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;

/**
 * A provider for test fixture data. Such a provider is required by {@link TestEnvironment} instances.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 * @see TestEnvironment
 */
public interface TestFixture {
    /**
     * Insert some test data into a test Mongo database via the provided <code>mongoClient</code>.
     *
     * @param mongoClient The client to access the Mongo database hosting the test data
     * @param insertCompleteHandler The handler called after inserting the data has completed
     */
    void insertTestData(final MongoClient mongoClient, final Handler<AsyncResult<Void>> insertCompleteHandler);
}
