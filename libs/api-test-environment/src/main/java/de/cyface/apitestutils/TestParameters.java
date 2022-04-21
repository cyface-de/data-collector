/*
 * Copyright 2019-2022 Cyface GmbH
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
package de.cyface.apitestutils;

import org.apache.commons.lang3.Validate;

import de.cyface.apitestutils.fixture.TestFixture;
import io.vertx.core.MultiMap;

/**
 * Parameters used for a single run of the API test
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
@SuppressWarnings("unused") // Part of the API
public final class TestParameters {

    /**
     * The fixture with the data required to run the test.
     */
    private final TestFixture testFixture;
    /**
     * The expected result.
     */
    private final String expectedResult;
    /**
     * The API endpoint used to ask for a data access.
     */
    private final String endpoint;
    /**
     * The header fields to attach to the request.
     */
    private final MultiMap headers;

    /**
     * Creates a new completely initialized instance of this class. All attributes are read only.
     *
     * @param testFixture The fixture with the data required to run the test
     * @param expectedResult The expected export
     * @param endpoint The API endpoint used to ask for a data export
     */
    @SuppressWarnings("unused") // Part of the API
    public TestParameters(final TestFixture testFixture, final String expectedResult, final String endpoint) {
        this(testFixture, expectedResult, endpoint, MultiMap.caseInsensitiveMultiMap());
    }

    /**
     * Creates a new completely initialized instance of this class. All attributes are read only.
     *
     * @param testFixture The fixture with the data required to run the test
     * @param expectedResult The expected export
     * @param endpoint The API endpoint used to ask for a data export
     * @param headers The header fields to attach to the request.
     */
    public TestParameters(final TestFixture testFixture, final String expectedResult, final String endpoint,
            final MultiMap headers) {
        this.testFixture = Validate.notNull(testFixture);
        this.expectedResult = Validate.notEmpty(expectedResult);
        this.endpoint = Validate.notEmpty(endpoint);
        this.headers = headers;
    }

    /**
     * @return The fixture with the data required to run the test
     */
    @SuppressWarnings("unused") // Part of the API
    public String getExpectedResult() {
        return expectedResult;
    }

    /**
     * @return The expected result
     */
    @SuppressWarnings("unused") // Part of the API
    public TestFixture getTestFixture() {
        return testFixture;
    }

    /**
     * @return The API endpoint used to ask for a data access
     */
    @SuppressWarnings("unused") // Part of the API
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @return The header fields to attach to the request.
     */
    @SuppressWarnings("unused") // Part of the API
    public MultiMap getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        return "TestParameters{" +
                "testFixture=" + testFixture +
                ", expectedResult='" + expectedResult + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", headers=" + headers +
                '}';
    }
}
