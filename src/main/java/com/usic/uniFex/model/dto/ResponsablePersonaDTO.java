package com.usic.uniFex.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ResponsablePersonaDTO {
      private Long responsableId; // null si será nuevo
  private Long personaId;     // null si será nueva
  private String nombre;
  private String paterno;
  private String materno;
  private String ci;
  private String correo;
  private String celular;
  private boolean eliminar;   // marcar para borrar
}
