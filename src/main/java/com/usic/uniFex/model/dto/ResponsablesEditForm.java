package com.usic.uniFex.model.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter @Setter
public class ResponsablesEditForm {
      private Long entidadId;
  private Long inscripcionId;
  @Size(max = 2)
  private List<ResponsablePersonaDTO> responsables = new ArrayList<>();
}
