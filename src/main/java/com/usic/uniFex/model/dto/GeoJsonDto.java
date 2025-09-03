package com.usic.uniFex.model.dto;

import java.util.List;
import java.util.Map;

public record GeoJsonDto(
        String type,
        List<Map<String, Object>> features) {
}