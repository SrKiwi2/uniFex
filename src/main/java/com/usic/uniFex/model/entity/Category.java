package com.usic.uniFex.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
  
    @Column(nullable=false, unique=true, length=40)
    private String code; // p.ej. TECH, FOOD
  
    @Column(nullable=false, length=120)
    private String nameEs;
  
    private String nameEn;
    private String namePt;
  
    @Column(length=7) // #RRGGBB
    private String colorHex;
}