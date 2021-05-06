/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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
