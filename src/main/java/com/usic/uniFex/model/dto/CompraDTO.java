package com.usic.uniFex.model.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class CompraDTO {
       private List<Item> carrito;
    private DatosPersona datosPersona;

    @Setter
    @Getter
    public static class Item {
        // Acepta varias formas del nombre
        @JsonAlias({"descripcion","nombre","nombreProducto"})
        public String descripcion;

        @JsonAlias({"precioUnitario","precio"})
        public double precioUnitario;

        @JsonAlias({"cantidad","qty","cantidadSolicitada"})
        public int cantidad;
    }

    @Setter
    @Getter
    public static class DatosPersona {
        private String nombre;
        private String apellidos;
        @JsonAlias({"ciNit", "ci", "nit"})
        public String numeroDocumento;
        public String fechaNacimiento; // yyyy-MM-dd
        public String correo;
    }

        public DatosCiente datosCliente;
    public List<Item> items;
}
