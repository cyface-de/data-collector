/*
 * Copyright 2019-2021 Cyface GmbH
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

import de.cyface.apitestutils.fixture.TestFixture;
import org.apache.commons.lang3.Validate;

/**
 * Parameters used for a single run of the API test
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
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
   * Creates a new completely initialized instance of this class. All attributes are read only.
   *
   * @param testFixture The fixture with the data required to run the test
   * @param expectedResult The expected export
   * @param endpoint The API endpoint used to ask for a data export
   */
  public TestParameters(final TestFixture testFixture, final String expectedResult,
                        final String endpoint) {
    Validate.notNull(testFixture);
    Validate.notEmpty(expectedResult);
    Validate.notEmpty(endpoint);

    this.testFixture = testFixture;
    this.expectedResult = expectedResult;
    this.endpoint = endpoint;
  }

  /**
   * @return The fixture with the data required to run the test
   */
  public String getExpectedResult() {
    return expectedResult;
  }

  /**
   * @return The expected result
   */
  public TestFixture getTestFixture() {
    return testFixture;
  }

  /**
   * @return The API endpoint used to ask for a data access
   */
  public String getEndpoint() {
    return endpoint;
  }
}
