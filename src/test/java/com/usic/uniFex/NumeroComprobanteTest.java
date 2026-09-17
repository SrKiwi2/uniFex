package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.service.RegistroVentaService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NumeroComprobanteTest {
    @InjectMocks RegistroVentaService registro;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void conservaCodigosLargosYCerosInicialesEnJson() throws Exception {
        String numero = "000ABC-" + "9".repeat(93);
        var venta = mapper.readValue("{\"numComprobante\":\"" + numero + "\"}", RegistroVentaService.NuevaVenta.class);
        assertThat(venta.numComprobante()).isEqualTo(numero);
        var inscripcion = new Inscripcion();
        inscripcion.setNumComprobante(venta.numComprobante());
        try (var fabrica = Validation.buildDefaultValidatorFactory()) {
            assertThat(fabrica.getValidator().validateProperty(inscripcion, "numComprobante")).isEmpty();
            inscripcion.setNumComprobante(numero + "9");
            assertThat(fabrica.getValidator().validateProperty(inscripcion, "numComprobante")).hasSize(1);
        }
    }

    @Test void aceptaClientesAnterioresQueMandanNumeroONull() throws Exception {
        var venta = mapper.readValue("{\"numComprobante\":9223372036854775807}", RegistroVentaService.NuevaVenta.class);
        assertThat(venta.numComprobante()).isEqualTo("9223372036854775807");
        assertThat(mapper.readValue("{}", RegistroVentaService.NuevaVenta.class).numComprobante()).isNull();
    }

    @Test void rechazaExcesoAntesDeRegistrarVentaOAdjuntarArchivo() throws Exception {
        String numero = "9".repeat(101);
        var venta = mapper.readValue("{\"numComprobante\":\"" + numero + "\"}", RegistroVentaService.NuevaVenta.class);
        var resultado = registro.registrar(venta, 1L);
        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.mensaje()).contains("100 caracteres");
        var adjunto = registro.adjuntarComprobante(1L, null, "Banco", numero, 1L);
        assertThat(adjunto.ok()).isFalse();
        assertThat(adjunto.mensaje()).contains("100 caracteres");
    }
}
