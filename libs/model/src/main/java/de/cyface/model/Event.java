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
package de.cyface.model;

import org.apache.commons.lang3.Validate;

import java.util.Arrays;

/**
 * A user interaction event, that occurred during a measurement. These events are uploaded alongside the data, as an
 * event log file in a proprietary binary format.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @since 2.0.1
 * @version 1.1.0
 */
public final class Event {
    /**
     * The free form <code>String</code> value of this event. This might be <code>null</code>, if the event has no such
     * value.
     */
    private final String value;
    /**
     * The {@code EventType} collected by this {@link Event}.
     */
    private final EventType type;
    /**
     * The timestamp at which this {@code Event} was captured in milliseconds since 1.1.1970.
     */
    private final long timestamp;

    /**
     * @param value The free form <code>String</code> value of this event. This might be <code>null</code>, if the event
     *            has no such value
     * @param type The {@code EventType} collected by this {@link Event}
     * @param timestamp The timestamp at which this {@code Event} was captured in milliseconds since 1.1.1970
     */
    public Event(final EventType type, final long timestamp, final String value) {
        Validate.isTrue(timestamp >= 0L, "Event timestamp must be larger/equal then zero, but was {}", timestamp);

        this.value = value;
        this.type = type;
        this.timestamp = timestamp;
    }

    /**
     * @return The free form <code>String</code> value of this event. This might be <code>null</code>, if the event has
     *         no such value
     */
    public String getValue() {
        return value;
    }

    /**
     * @return The {@code EventType} collected by this {@link Event}
     */
    public EventType getType() {
        return type;
    }

    /**
     * @return The timestamp at which this {@code Event} was captured in milliseconds since 1.1.1970
     */
    public long getTimestamp() {
        return timestamp;
    }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Event event = (Event)o;

    // (1) not available in this minSDK version (2) value is null when a LIFECYCLE event is stored
    // noinspection EqualsReplaceableByObjectsCall
    return timestamp == event.timestamp &&
      (value == null ? event.value == null : value.equals(event.value)) &&
      type == event.type;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[] {type, timestamp, value});
  }

  @Override
  public String toString() {
    return "Event{" +
      ", type=" + type +
      ", timestamp=" + timestamp +
      ", value='" + value + '\'' +
      '}';
  }

    /**
     * Defines the {@code EventType}s which may be collected.
     * <p>
     * An example are the use of the life-cycle methods such as start, pause, resume, etc. which are required to
     * slice {@link Measurement}s into {@link Track}s before they are resumed.
     *
     * @author Armin Schnabel
     * @version 1.0.1
     * @since 2.0.0
     */
    public enum EventType {
      /**
       * This event occurs at the start of a measurement.
       */
      LIFECYCLE_START("LIFECYCLE_START"),
      /**
       * This event occurs when a measurement is paused.
       */
      LIFECYCLE_PAUSE("LIFECYCLE_PAUSE"),
      /**
       * This event occurs when a measurement is resumed.
       */
      LIFECYCLE_RESUME("LIFECYCLE_RESUME"),
      /**
       * This event occurs when a measurement is stopped.
       */
      LIFECYCLE_STOP("LIFECYCLE_STOP"),
      /**
       * This event occurs if the mode of transportation changes during a measurement.
       */
      MODALITY_TYPE_CHANGE("MODALITY_TYPE_CHANGE");

      /**
       * The event types identifier within the database.
       */
      private final String databaseIdentifier;

      /**
       * Creates a new <code>EventType</code> enum case. Since this is an enumeration the constructor is not visible.
       *
       * @param databaseIdentifier The event types identifier within the database
       */
      EventType(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
      }

      /**
       * @return The event types identifier within the database
       */
      public String getDatabaseIdentifier() {
        return databaseIdentifier;
      }
    }
}
