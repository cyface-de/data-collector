/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils.fixture;

import org.apache.commons.lang3.Validate;

import io.vertx.core.*;
import io.vertx.ext.mongo.MongoClient;

/**
 * A fixture providing data to use for testing the deserialization
 *
 * TODO: This was used by data-provider and exporter tests but they now access unpacked measurements instead.
 * I moved it into this class as I think we need it to test the deserializer.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
public final class DeserializerTestFixture implements TestFixture {
    /**
     * The name of the test group to export test data of.
     */
    private static final String TEST_GROUP = "testGroup";
    /**
     * The name of the user to add test data for in group-data accessing tests.
     */
    public static final String TEST_GROUP_USER_USERNAME = TEST_GROUP + "1";
    /**
     * The id of the test measurement file to create.
     */
    private static final String TEST_MEASUREMENT_FILES_ID = "5dce7fe38096c9445e1c8d54";
    /**
     * The id of the test events file to create.
     */
    private static final String TEST_EVENTS_FILES_ID = "5dce7ce68096c9445e1c8d48";
    /**
     * The user which is used for authentication in the test.
     */
    public static final String TEST_USER_NAME = "admin";
    /**
     * The identifier of the measurement used during the test.
     */
    private final String measurementIdentifier;
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
    public DeserializerTestFixture(final String measurementIdentifier, final String deviceIdentifier,
                                   final String dataOwnerUsername) {
        Validate.notEmpty(measurementIdentifier);
        Validate.notEmpty(deviceIdentifier);

        this.measurementIdentifier = measurementIdentifier;
        this.deviceIdentifier = deviceIdentifier;
        this.dataOwnerUsername = dataOwnerUsername;
    }

    @Override
    public void insertTestData(MongoClient mongoClient, Handler<AsyncResult<Void>> insertCompleteHandler) {
        final Promise<Void> createAuthUserPromise = Promise.promise();
        final Promise<Void> createTestUserPromise = Promise.promise();
        final Promise<Void> createBinaryTestFileDocumentPromise = Promise.promise();
        final Promise<Void> createEventTestFileDocumentPromise = Promise.promise();
        final Promise<Void> createBinaryTestFileChunkPromise = Promise.promise();
        final Promise<Void> createEventTestFileChunkPromise = Promise.promise();
        final CompositeFuture synchronizer = CompositeFuture.all(createAuthUserPromise.future(),
                createTestUserPromise.future(), createBinaryTestFileChunkPromise.future(),
                createEventTestFileChunkPromise.future(), createBinaryTestFileDocumentPromise.future(),
                createEventTestFileDocumentPromise.future());
        synchronizer.onComplete(result -> {
            if (result.succeeded()) {
                insertCompleteHandler.handle(Future.succeededFuture());
            } else {
                insertCompleteHandler.handle(Future.failedFuture(result.cause()));
            }
        });

        new TestUser(TEST_USER_NAME, "secret", TEST_GROUP + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX).insert(
                mongoClient,
                createAuthUserPromise);
        new TestUser(TEST_GROUP_USER_USERNAME, "secret", TEST_GROUP + DatabaseConstants.USER_GROUP_ROLE_SUFFIX)
                .insert(mongoClient, createTestUserPromise);

        new TestFilesDocument(dataOwnerUsername, DatabaseConstants.METADATA_FILE_TYPE_CCYF,
                TEST_MEASUREMENT_FILES_ID, measurementIdentifier, deviceIdentifier).insert(mongoClient,
                        createBinaryTestFileDocumentPromise);
        new TestFilesDocument(dataOwnerUsername, DatabaseConstants.METADATA_FILE_TYPE_CCYFE,
                TEST_EVENTS_FILES_ID, measurementIdentifier, deviceIdentifier).insert(mongoClient,
                        createEventTestFileDocumentPromise);

        // noinspection SpellCheckingInspection
        final String eventsBinary = "Y2BkYGBgZmBgzLs479gvBhAPxmZlYA939PH29HOHCJ0TYGBiYAAA";
        // noinspection SpellCheckingInspection
        final String measurementBinary = "rdnxq84HHMXxg6X9IMkPkjaZSZIkySTZTTKZJEmSdJMkSZIks+0mk2QySZIkkyTJJEnSzW53ZpJJ"
                + "kmSSZCZJkiTt3rP23LP2Oc/ZD3vXt6e+r+/zB3w66AWgd9fT1vV83fV8CfRa/evBa60t037oeLX187ntS7Z91Oebzq/QqM/37pu"
                + "WT777o43f9N3x1zc/ffj3v1o+/nZDt336+7sfW/nmf/GhwUcFHx98SvAZwecGXxR8WfDVwTcE3xx8R/C9wQ8FPx78TPCLwS8Hvx"
                + "H8bvBHwZ8Hf9PcL78XvH/wwcGHBx8TfGLwluCzgs8PviT4iuBrg28KvjX4ruD7gx8JfjL4ueCXgl8Nfiv4/eBPgr8M/q65//x+8"
                + "IHBPwg+Mvi44JODTw8+J/jC4EuDrwq+Pnhb8O3B9wQ/GPxY8NPBLwTvDH49+J3gD4M/C/66uV/pHbxf8EHBhwUfHXxC8KnBZwaf"
                + "F3xx8OXB1wTfGHxL8J3B9wU/HPxE8LPB24NfCX4z+L3gj4O/CP62uf/SN/iA4EOCjwg+Nvik4NOCzw6+IHhr8JXB1wVv3KXGtwX"
                + "fHfxA8KPBTwU/H7wj+LXgt4M/CP40+Kvm3nO//7v/5ENho4+CjT4eNvqU4DNgo8+Fjb4INvoy2OirYaNvCL4ZNvoO2Oh7YaMfCn"
                + "4cNvoZ2OgXYaNfho1+Azb63eCPYKM/h43+BrZu77nvjfeHjT44+HDY6GNgo0+Ejd4SfBZs9Pmw0ZfARl8BG30tbPRNwbfCRt8FG"
                + "30/bPQjsNFPwkY/F/wSbPSrsNFvwUa/H/wJbPSXsNHfwdbtPfe98YGw0T8IPhI2+jjY6JNho0+HjT4HNvrC4Etho6+Cjb4eNnpb"
                + "8O2w0ffARj8IG/0YbPTTsNEvBO+EjX4dNvod2OgPYaM/g43+urn33PfG+8FGHwQbfVjw0bDRJ8BGnwobfSZs9Hmw0RcHXw4bfQ1"
                + "s9I2w0bfARt8JG31f8MOw0U/ARj8LG709+BXY6Ddho9+Djf4YNvoL2Ohvm3vPfW98AGz0IbDRR8BGHwsbfRJs9Gmw0WfDRl8AG7"
                + "01+ErY6Otgo8vuXPo22Oi7YaMfgI1+FDb6Kdjo52Gjd8BGvwYb/TZs9Aew0Z/CRn8FW7fr/t75RWvXb/tnvy1t73HZ30uX/b30x"
                + "n1uXPb30mV/L13299Jlfy9d9vfSG/e5cdnfS5f9vXTZ30s/FFz299Jlfy9d9vfSZX8vXfb30hv3uXHZ30uX/b102d8r1/29dNnf"
                + "Sx8cXPb30mV/L13299Jbgsv+Xrrs76XL/l667O+ly/5eeuM+Ny77e+myv5cu+3vpR4LL/l76ueCyv5cu+3vpsr+Xfj+47O+ly/5"
                + "euuzvlev+Xrrs76U37nPjsr+XLvt76bK/lz49uOzvpS8MLvt76bK/ly77e+ltwWV/L13299Jlfy9d9vfSZX8vvXGfG5f9vXTZ30"
                + "uX/b30h8Flfy/9dXPX/b102d9Ll/299GHBZX8vXfb30mV/L13299Jlfy+9cZ8bl/29dNnfS5f9vfQtwWV/L31fcNnfS5f9vXTZ3"
                + "0tvDy77e+myv5cu+3vpsr+XLvt76Y37vHbd30uX/b102d9LHxFc9vfSJwWX/b102d9Ll/299Nbgsr+XLvt76bK/ly77e+myv5cu"
                + "+3vpsr+XLvt76bK/l94RXPb30m8Hl/29dNnfS5f9/Z/+Jw==";

        new TestChunksDocument(TEST_MEASUREMENT_FILES_ID, measurementBinary).insert(mongoClient,
                createBinaryTestFileChunkPromise);
        new TestChunksDocument(TEST_EVENTS_FILES_ID, eventsBinary).insert(mongoClient, createEventTestFileChunkPromise);
    }

}
