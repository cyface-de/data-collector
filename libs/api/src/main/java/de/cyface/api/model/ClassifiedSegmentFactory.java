package de.cyface.api.model;

import io.vertx.core.json.JsonObject;

/**
 * A factory for {@link ClassifiedSegment}s.
 *
 * @author Armin Schnabel
 */
public interface ClassifiedSegmentFactory<T extends ClassifiedSegment> {
    T build(final JsonObject segment);
}
