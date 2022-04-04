/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.api;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.cyface.api.model.Role;

/**
 * Tests whether the roles constructions from database values works as expected.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.4.0
 */
@SuppressWarnings({"SpellCheckingInspection"})
public class RoleTest {

    @ParameterizedTest
    @MethodSource("testParameters")
    void test_happyPath(final TestParameters parameters) {
        // Arrange

        // Act
        final var oocut = new Role(parameters.databaseValue);

        // Assert
        assertThat(oocut, is(equalTo(parameters.expectedResult)));
    }

    @Test
    void test_managerWithoutGroup_throwsException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Role("_manager"));
    }

    @Test
    void test_groupWithoutType_throwsException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Role("project"));
    }

    @SuppressWarnings("unused")
    static Stream<TestParameters> testParameters() {
        return Stream.of(
                new TestParameters("guest", new Role(Role.Type.GUEST, null)),
                new TestParameters("project_user", new Role(Role.Type.GROUP_USER, "project")),
                new TestParameters("project_manager", new Role(Role.Type.GROUP_MANAGER, "project")),
                new TestParameters("pro-ject_manager", new Role(Role.Type.GROUP_MANAGER, "pro-ject")));
    }

    private static class TestParameters {
        String databaseValue;
        Role expectedResult;

        public TestParameters(String databaseValue, Role expectedResult) {
            this.databaseValue = databaseValue;
            this.expectedResult = expectedResult;
        }
    }
}
