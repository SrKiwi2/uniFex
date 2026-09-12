package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.usic.uniFex.model.service.CredencialCodigoService;

/**
 * El codigo firmado que viaja en el QR de una credencial.
 *
 * Existe por un fallo que no se ve en una prueba con un caso suelto: la firma es base64url y
 * ese alfabeto INCLUYE el guion, el mismo caracter que separa las tres partes del codigo. Con
 * un {@code split("-")} sin limite, una credencial de cada seis se partia en cuatro trozos y se
 * rechazaba — el papel bien impreso, la firma correcta, y el lector de la puerta diciendo que
 * no vale. Aqui se recorren cientos de identificadores para que el caso caiga si o si.
 *
 * No necesita base de datos ni contexto de Spring: el servicio solo depende del secreto.
 *
 * Ejecutar:  mvnw.cmd test "-Dtest=CredencialCodigoTest"
 */
class CredencialCodigoTest {

    private static CredencialCodigoService servicio() {
        CredencialCodigoService s = new CredencialCodigoService();
        ReflectionTestUtils.setField(s, "secreto",
                "secreto-de-prueba-suficientemente-largo-para-hmac-sha256");
        return s;
    }

    @Test
    void todoCodigoGeneradoVuelveASuResponsable() {
        CredencialCodigoService s = servicio();
        int conGuion = 0;
        for (long id = 1; id <= 800; id++) {
            String codigo = s.codigoDe(id);
            // Tres partes solo si se cuenta bien: el prefijo, el id y TODO lo que venga detras.
            if (codigo.split("-").length > 3) conGuion++;
            assertThat(s.responsableDe(codigo))
                    .as("el codigo %s tiene que devolver el responsable %s", codigo, id)
                    .isEqualTo(id);
        }
        // Si esto fuera cero, la prueba estaria pasando sin ejercer el caso que la motiva.
        assertThat(conGuion)
                .as("con 800 codigos tiene que haber firmas con guiones dentro")
                .isGreaterThan(0);
    }

    @Test
    void elCodigoSeLeeAunqueVengaEnMinusculasOConEspacios() {
        CredencialCodigoService s = servicio();
        String codigo = s.codigoDe(42L);
        assertThat(s.responsableDe("  " + codigo.toLowerCase() + "  ")).isEqualTo(42L);
    }

    @Test
    void unaFirmaInventadaNoAbreNada() {
        CredencialCodigoService s = servicio();
        String codigo = s.codigoDe(7L);
        String[] p = codigo.split("-", 3);

        assertThat(s.responsableDe(p[0] + "-" + p[1] + "-AAAAAAAAAAAA")).isNull();
        // Reusar una firma autentica con otro id: es el intento evidente de quien ve el codigo.
        assertThat(s.responsableDe(p[0] + "-" + Long.toString(8L, 36).toUpperCase() + "-" + p[2])).isNull();
        assertThat(s.responsableDe("XXX-" + p[1] + "-" + p[2])).isNull();
        assertThat(s.responsableDe("cualquier-cosa")).isNull();
        assertThat(s.responsableDe(null)).isNull();
    }

    @Test
    void reimprimirDaSiempreElMismoCodigo() {
        // Es la razon de derivarlo del id en vez de guardarlo: lo ya impreso y entregado no
        // puede dejar de valer porque alguien vuelva a generar la credencial.
        assertThat(servicio().codigoDe(123L)).isEqualTo(servicio().codigoDe(123L));
    }

    @Test
    void laUrlDelQrNoDuplicaLaBarra() {
        CredencialCodigoService s = servicio();
        String c = s.codigoDe(5L);
        assertThat(s.urlPublica("https://feria.uap.edu.bo/", c)).isEqualTo("https://feria.uap.edu.bo/credencial/" + c);
        assertThat(s.urlPublica("https://feria.uap.edu.bo", c)).isEqualTo("https://feria.uap.edu.bo/credencial/" + c);
    }
}
