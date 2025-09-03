package com.usic.uniFex.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.*;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class MapArea {
      @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable=false, length=120)
  private String name;

  @Column(nullable=false, length=24)
  private String type; // STAND/BAÑO/ESCENARIO/COMIDA/INFO/EMERGENCIA

  @Column(columnDefinition = "text", nullable=false)
  private String polygonGeojson; // GeoJSON Polygon en CRS.Simple (px)

  private Integer centroidX; // px
  private Integer centroidY; // px
}
