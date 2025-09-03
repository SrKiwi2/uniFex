package com.usic.uniFex.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IMapAreaService;
import com.usic.uniFex.model.dto.GeoJsonDto;
import com.usic.uniFex.util.GeoJsonUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MapController {

    private final IMapAreaService areaRepo;

    @GetMapping("/api/map/areas")
    public GeoJsonDto listAreas() {
        var features = areaRepo.findAll().stream()
                .map(a -> GeoJsonUtil.toFeature(a, Map.of()))
                .toList();
        return new GeoJsonDto("FeatureCollection", features);
    }
}
