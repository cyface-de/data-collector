package de.cyface.api.model;

import io.vertx.core.json.JsonObject;

public class ClassifiedMeasurementSegmentFactory implements ClassifiedSegmentFactory<ClassifiedMeasurementSegment> {

    public ClassifiedMeasurementSegment build(final JsonObject segment){
        return new ClassifiedMeasurementSegment(segment);
    }
}
