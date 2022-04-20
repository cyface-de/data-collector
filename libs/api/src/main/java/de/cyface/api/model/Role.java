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
package de.cyface.api.model;

import java.util.Objects;

import org.apache.commons.lang3.Validate;

import de.cyface.api.DatabaseConstants;

/**
 * This class represents a user role of a specific {@link Type}.
 *
 * @author Armin Schnabel
 * @since 6.4.0
 * @version 1.0.0
 */
public class Role {

    /**
     * The {@link Type} of this role.
     */
    private final Type type;
    /**
     * The group the user with that role is part of. {@code Null} if the users is a {@link Type#GUEST}.
     */
    private final String group;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param type The {@link Type} of this role. Only {@link Type#GUEST} roles are allowed without group. Use
     *            {@link Role(Type, String)} for other roles.
     */
    public Role(Type type) {
        Validate.isTrue(type.equals(Type.GUEST), String.format("Guest role expected, but is: %s", type));
        this.type = type;
        this.group = null;
    }

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param type The {@link Type} of this role.
     * @param group The group the user with that role is part of. {@code Null} if the users is a {@link Type#GUEST}.
     */
    public Role(Type type, String group) {
        if (type != Type.GUEST) {
            Validate.notEmpty(group,
                    String.format("Only guest users are not part of a user group but type is: %s", type));
        }
        this.group = group;
        this.type = type;
    }

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param databaseValue A role entry from the database.
     */
    public Role(final String databaseValue) {
        final var isGuest = databaseValue.matches(Type.GUEST.getRegex());
        final var isGroupUser = databaseValue.matches(Type.GROUP_USER.getRegex());
        final var isGroupManager = databaseValue.matches(Type.GROUP_MANAGER.getRegex());
        if (isGuest) {
            this.type = Type.GUEST;
            this.group = null;
        } else if (isGroupUser) {
            this.type = Type.GROUP_USER;
            final var group = databaseValue.substring(0,
                    databaseValue.length() - DatabaseConstants.USER_GROUP_ROLE_SUFFIX.length());
            this.group = Validate.notEmpty(group);
        } else if (isGroupManager) {
            this.type = Type.GROUP_MANAGER;
            final var group = databaseValue.substring(0,
                    databaseValue.length() - DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX.length());
            this.group = Validate.notEmpty(group);
        } else {
            throw new IllegalArgumentException(String.format("Unknown role format: %s", databaseValue));
        }
    }

    /**
     * @return The group the user with that role is part of. {@code Null} if the users is a {@link Type#GUEST}.
     */
    public String getGroup() {
        return group;
    }

    /**
     * @return The {@link Type} of this role.
     */
    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Role{" +
                "type=" + type +
                ", group='" + group + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return type == role.type && Objects.equals(group, role.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, group);
    }

    /**
     * The type of {@link Role}.
     *
     * @author Armin Schnabel
     * @since 6.4.0
     * @version 1.0.0
     */
    public enum Type {
        /**
         * A guest user who signed up himself and is not part of a user group.
         */
        GUEST(DatabaseConstants.GUEST_ROLE),
        /**
         * A group user who collects data for a group manager.
         */
        GROUP_USER("([a-zA-Z0-9\\-]{1,32})" + DatabaseConstants.USER_GROUP_ROLE_SUFFIX),
        /**
         * A group manager who can access all data of all users of its group.
         */
        GROUP_MANAGER("([a-zA-Z0-9\\-]{1,32})" + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX);

        /**
         * The regex which represents this role type.
         */
        private final String regex;

        /**
         * Creates a new completely initialized multipart form attribute.
         *
         * @param regex The regex which represents this role type.
         */
        Type(final String regex) {
            this.regex = regex;
        }

        /**
         * @return The regex which represents this role type.
         */
        public String getRegex() {
            return regex;
        }
    }
}
