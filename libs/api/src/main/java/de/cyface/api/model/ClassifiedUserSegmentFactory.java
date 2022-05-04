package de.cyface.api.model;

import io.vertx.core.json.JsonObject;

public class ClassifiedUserSegmentFactory implements ClassifiedSegmentFactory<ClassifiedSegment> {

    @Override
    public ClassifiedSegment build(final JsonObject segment){
        return new ClassifiedSegment(segment);
    }
}
