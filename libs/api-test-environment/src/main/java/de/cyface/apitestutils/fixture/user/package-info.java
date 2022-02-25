/*
 * Copyright 2020-2022 Cyface GmbH
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
/**
 * This package contains classes to group data about Cyface user accounts required to build a test fixture.
 * There are two different types of users. One is created via the normal user registration process and one directly via
 * the internal Cyface management API.
 * The first is represented in a test fixture via {@link de.cyface.apitestutils.fixture.user.ActivatableTestUser}
 * instance, which is capable of simulating whether that use was already activated and which activation token to use.
 * The second is represented as a {@link de.cyface.apitestutils.fixture.user.DirectTestUser} instance, which does not
 * require any information about user activation.
 * 
 * @author Klemens Muthmann
 */
package de.cyface.apitestutils.fixture.user;