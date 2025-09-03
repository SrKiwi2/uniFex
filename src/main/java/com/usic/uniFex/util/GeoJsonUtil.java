package com.usic.uniFex.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.uniFex.model.entity.MapArea;

public final class GeoJsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeoJsonUtil() {
    }

    public static Map<String, Object> toFeature(MapArea a, Map<String, Object> propsExtra) {
        try {
            Map<String, Object> geometry = MAPPER.readValue(a.getPolygonGeojson(), new TypeReference<>() {
            });
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("id", a.getId());
            props.put("name", a.getName());
            props.put("type", a.getType());
            props.put("centroidX", a.getCentroidX());
            props.put("centroidY", a.getCentroidY());
            if (propsExtra != null)
                props.putAll(propsExtra);

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");
            feature.put("geometry", geometry);
            feature.put("properties", props);
            return feature;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid polygonGeojson for area id=" + a.getId(), e);
        }
    }
}
