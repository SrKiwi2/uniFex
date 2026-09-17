package com.usic.uniFex.whatsapp;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import com.usic.uniFex.model.dao.IInstanciaWhatsAppDao;
import com.usic.uniFex.model.entity.InstanciaWhatsApp;
import com.usic.uniFex.model.service.InstanciaWhatsAppService;

/** PostgreSQL real, en un esquema aislado: no modifica las instancias de desarrollo. */
@SpringBootTest(classes = InstanciaWhatsAppTest.Configuracion.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "pruebas-whatsapp"})
class InstanciaWhatsAppTest {
    private static final String ESQUEMA = "prueba_whatsapp_" + UUID.randomUUID().toString().replace("-", "");
    private static final Properties PROPIEDADES = new Properties();

    @Configuration
    @Profile("pruebas-whatsapp")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = InstanciaWhatsApp.class)
    @EnableJpaRepositories(basePackageClasses = InstanciaWhatsAppTest.class, considerNestedRepositories = true)
    @Import(InstanciaWhatsAppService.class)
    static class Configuracion {}

    interface DaoPrueba extends IInstanciaWhatsAppDao {}

    @DynamicPropertySource
    static void esquema(DynamicPropertyRegistry registro) throws Exception {
        try (var entrada = Files.newInputStream(Path.of("src/main/resources/application-dev.properties"))) {
            PROPIEDADES.load(entrada);
        }
        String url = PROPIEDADES.getProperty("spring.datasource.url");
        var direccion = java.net.URI.create(url.substring(5));
        if (!java.util.Set.of("localhost", "127.0.0.1").contains(direccion.getHost())) {
            throw new IllegalStateException("Las pruebas requieren PostgreSQL local.");
        }
        try (var conexion = conectar(); var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE SCHEMA " + ESQUEMA);
            conexion.setSchema(ESQUEMA);
            sentencia.execute(Files.readString(Path.of("src/main/resources/db/reserva/V31__instancias_whatsapp.sql")));
            // La migracion se puede volver a aplicar sin perder registros ni fallar.
            sentencia.execute(Files.readString(Path.of("src/main/resources/db/reserva/V31__instancias_whatsapp.sql")));
        }
        registro.add("spring.datasource.hikari.schema", () -> ESQUEMA);
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    private static Connection conectar() throws Exception {
        return DriverManager.getConnection(PROPIEDADES.getProperty("spring.datasource.url"),
                PROPIEDADES.getProperty("spring.datasource.username"), PROPIEDADES.getProperty("spring.datasource.password"));
    }

    @AfterAll static void eliminarEsquemaDePrueba() throws Exception {
        try (var conexion = conectar(); var sentencia = conexion.createStatement()) {
            sentencia.execute("DROP SCHEMA " + ESQUEMA + " CASCADE");
        }
    }

    @Autowired InstanciaWhatsAppService servicio;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() { jdbc.execute("TRUNCATE instancia_whatsapp RESTART IDENTITY"); }

    @Test void primeraActivaYEdicionConservaClaveSinDevolverla() throws Exception {
        var primera = servicio.guardar(null, "Principal", "http://localhost:9191/message", "FEXPO UAP", "secreto");
        assertThat(primera.activa()).isTrue();
        assertThat(primera.urlApi()).endsWith("/message/");
        assertThat(servicio.guardar(null, "Reserva", "https://example.test/message/", "Otra", "otra-clave").activa()).isFalse();
        servicio.guardar(primera.id(), "Editada", primera.urlApi(), "Nueva", "");
        assertThat(servicio.activa().orElseThrow().getClaveApi()).isEqualTo("secreto");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(servicio.listar()))
                .doesNotContain("secreto", "claveApi");
        servicio.guardar(primera.id(), "Editada", primera.urlApi(), "Nueva", "reemplazo");
        assertThat(servicio.activa().orElseThrow().getClaveApi()).isEqualTo("reemplazo");
    }

    @Test void cambioAtomicoYNoPermiteDejarNingunaActiva() {
        var primera = servicio.guardar(null, "Uno", "https://example.test/message/", "Uno", "clave");
        var segunda = servicio.guardar(null, "Dos", primera.urlApi(), "Dos", "clave");
        assertThatThrownBy(() -> servicio.cambiarEstado(primera.id(), false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        servicio.cambiarEstado(segunda.id(), true);
        assertThat(servicio.listar().stream().filter(i -> i.activa()).count()).isEqualTo(1);
        assertThat(servicio.activa().orElseThrow().getId()).isEqualTo(segunda.id());
        assertThatThrownBy(() -> servicio.cambiarEstado(-1L, true)).hasMessageContaining("404");
        assertThat(servicio.activa().orElseThrow().getId()).isEqualTo(segunda.id());
    }

    @Test void importacionSoloSiEstaVacia() {
        servicio.importarInicial("http://localhost:9191/message/", "Original", "clave");
        servicio.importarInicial("https://example.test/message/", "Reemplazo", "otra");
        assertThat(servicio.listar()).hasSize(1);
        assertThat(servicio.activa().orElseThrow().getInstancia()).isEqualTo("Original");
    }

    @Test void validaDatosAntesDePersistir() {
        for (String url : new String[]{"ftp://example.test/", "https://user:pass@example.test/", "https://example.test/?x=1"}) {
            assertThatThrownBy(() -> servicio.guardar(null, "Prueba", url, "Instancia", "clave"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        }
        assertThatThrownBy(() -> servicio.guardar(null, "Prueba", "http://localhost/", "Instancia", ""))
                .hasMessageContaining("400");
        assertThat(servicio.listar()).isEmpty();
    }

    @Test void altasYActivacionesConcurrentesMantienenUnaSolaActiva() throws Exception {
        try (var ejecutor = Executors.newFixedThreadPool(8)) {
            var salida = new CountDownLatch(1);
            var altas = java.util.stream.IntStream.range(0, 8).mapToObj(n -> ejecutor.submit(() -> {
                salida.await();
                return servicio.guardar(null, "Instancia " + n, "http://localhost/message/", "I" + n, "clave");
            })).toList();
            salida.countDown();
            for (var alta : altas) alta.get(15, TimeUnit.SECONDS);
            assertThat(servicio.listar()).hasSize(8);
            assertThat(servicio.listar().stream().filter(i -> i.activa()).count()).isEqualTo(1);
            var activar = new CountDownLatch(1);
            var cambios = servicio.listar().stream().map(i -> ejecutor.submit(() -> {
                activar.await();
                return servicio.cambiarEstado(i.id(), true);
            })).toList();
            activar.countDown();
            for (var cambio : cambios) assertThat(cambio.get(15, TimeUnit.SECONDS).stream().filter(i -> i.activa()).count()).isEqualTo(1);
            assertThat(servicio.listar().stream().filter(i -> i.activa()).count()).isEqualTo(1);
        }
    }
}
