package com.usic.uniFex.model.dto;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PagoRequest {
    private DatosCliente datosCliente;
    private List<Item> items;
    private Map<String, Object> metadata; // aquí mandamos categoriaId, cantidad, ci, etc.

    @Getter @Setter
    public static class DatosCliente {
        public String nombres;
        public String apellidos;
        public String ciNit;
        public String fechaNacimiento; // yyyy-MM-dd o null
        public String correo;
    }

    @Getter @Setter
    public static class Item {
        public String descripcion;
        public double precioUnitario;
        public int cantidad;
    }
}
