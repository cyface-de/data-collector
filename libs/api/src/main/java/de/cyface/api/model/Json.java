/*
 * Copyright 2021 Cyface GmbH
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

import java.util.Arrays;

/**
 * This class allows structuring data in the Json format without Json dependencies.
 *
 * @author Armin Schnabel
 * @since 1.1.0
 */
public class Json {

    public static JsonArray jsonArray(final String... objects) {
        final var builder = new StringBuilder("[");
        Arrays.stream(objects).forEach(p -> builder.append(p).append(","));
        builder.deleteCharAt(builder.length() - 1); // remove trailing comma
        builder.append("]");
        return new JsonArray(builder.toString());
    }

    public static JsonObject jsonObject(final KeyValuePair... keyValuePairs) {
        final var builder = new StringBuilder("{");
        Arrays.stream(keyValuePairs).forEach(p -> builder.append(p.stringValue).append(","));
        builder.deleteCharAt(builder.length() - 1); // remove trailing comma
        builder.append("}");
        return new JsonObject(builder.toString());
    }

    public static KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key, final JsonArray value) {
        return new KeyValuePair("\"" + key + "\":" + value.stringValue);
    }

    public static KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key,
                                      final JsonObject value) {
        return new KeyValuePair("\"" + key + "\":" + value.stringValue);
    }

    public static KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key, final long value) {
        return new KeyValuePair("\"" + key + "\":" + value);
    }

    public static KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key, final boolean value) {
        return new KeyValuePair("\"" + key + "\":" + value);
    }

    public static KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key, final double value) {
        return new KeyValuePair("\"" + key + "\":" + value);
    }

    public static KeyValuePair jsonKeyValue(final String key, final String value) {
        return new KeyValuePair("\"" + key + "\":\"" + value + "\"");
    }

    public static class KeyValuePair {
        private final String stringValue;

        public KeyValuePair(String stringValue) {
            this.stringValue = stringValue;
        }

        public String getStringValue() {
            return stringValue;
        }
    }

    public static class JsonObject {
        private final String stringValue;

        public JsonObject(String stringValue) {
            this.stringValue = stringValue;
        }

        public String getStringValue() {
            return stringValue;
        }
    }

    public static class JsonArray {
        private final String stringValue;

        public JsonArray(String stringValue) {
            this.stringValue = stringValue;
        }

        public String getStringValue() {
            return stringValue;
        }
    }
}
