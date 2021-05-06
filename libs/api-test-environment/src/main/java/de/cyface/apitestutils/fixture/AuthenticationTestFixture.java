/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils.fixture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
public final class AuthenticationTestFixture implements TestFixture {

    @Override
    public void insertTestData(MongoClient mongoClient, Handler<AsyncResult<Void>> insertCompleteHandler) {
        final TestUser testUser = new TestUser("admin", "secret",
                "testGroup" + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX);
        testUser.insert(mongoClient, result -> {
            if (result.succeeded()) {
                insertCompleteHandler.handle(Future.succeededFuture());
            } else {
                insertCompleteHandler.handle(Future.failedFuture(result.cause()));
            }
        });
    }
}
