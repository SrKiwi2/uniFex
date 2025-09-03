package com.usic.uniFex.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
@Table(indexes = @Index(columnList = "slug", unique = true))
public class Stand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable=false, unique=true, length=80)
  private String slug; // stand-innovacion-a1

  @Column(nullable=false, length=140)
  private String name;

  @Column(length=160)
  private String orgName;

  @ManyToOne(optional=false)
  private Category category;

  @Column(length=240)
  private String summary;

  @Column(columnDefinition = "text")
  private String descriptionMd;

  private String contactName;
  private String contactPhone;
  private String socialUrl;

  @OneToOne
  private MapArea area;

  private Boolean isAccessible = Boolean.TRUE;
}