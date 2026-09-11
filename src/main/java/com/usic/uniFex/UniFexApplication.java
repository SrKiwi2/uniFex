package com.usic.uniFex;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.IService.UtilidadesService;
import com.usic.uniFex.model.service.GestionRolService;
import com.usic.uniFex.security.RolesSistema;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.model.entity.Usuario;

@SpringBootApplication
@EnableScheduling
public class UniFexApplication {

	@Autowired PasswordEncoder passwordEncoder;

	// Contrasenas iniciales de admin1/admin2. Solo se usan la primera vez que cada usuario
	// se crea (ver mas abajo); en produccion vienen de ADMIN1_PASSWORD/ADMIN2_PASSWORD y no
	// tienen default, para no recrear con una contrasena publica si el arranque encuentra
	// la tabla de usuarios vacia.
	@Value("${unifex.admin.admin1-password}")
	private String admin1Password;

	@Value("${unifex.admin.admin2-password}")
	private String admin2Password;

	private static final Logger logger = LoggerFactory.getLogger(UniFexApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(UniFexApplication.class, args);
	}

	@Bean
	ApplicationRunner init(IUsuarioService usuarioService, IPersonaService personaService, GestionRolService gestionRol, UtilidadesService utilidadesService, DataSource dataSource) {

		return args -> {

			aplicarEsquemaSiFalta(dataSource);

			logger.info("SISTEMA UNIFEXPO...");

			// Los CINCO roles del sistema, no dos. Antes solo se sembraban SUPER USUARIO y
			// ADMINISTRADOR, con lo que en una base nueva era imposible dar de alta a un
			// vendedor (ADMINISTRATIVO), a un control de puerta (CONTROL) o a asesoria: el
			// selector del modulo de usuarios solo ofrecia esos dos. La definicion vive en
			// security/RolesSistema y el sembrado es idempotente.
			gestionRol.asegurarRolesDelSistema();

			// admin1 es el SUPER USUARIO y admin2 el ADMINISTRADOR. Se nombran explicitamente
			// en vez de indexar el arreglo de roles: al crecer el catalogo, un "i % roles.length"
			// repartiria las cuentas semilla entre roles que no les tocan.
			Rol[] rolObjects = {
					gestionRol.delSistema(RolesSistema.SUPER_USUARIO),
					gestionRol.delSistema(RolesSistema.ADMINISTRADOR)
			};

			String[] cis = { "123456789", "987654321" };
			String[] nombres = { "PRIMER USUARIO", "SEGUNDO USUARIO" };
			String[] usuarios = { "admin1", "admin2" };
			String[] password = { admin1Password, admin2Password };

			for (int i = 0; i < cis.length; i++) {
				Persona persona = personaService.buscarPersonaPorCI(cis[i]);
				if (persona == null) {
					persona = new Persona();
					persona.setNombre(nombres[i]);
					persona.setPaterno("ApellidoP" + (i + 1));
					persona.setMaterno("ApellidoM" + (i + 1));
					persona.setCi(cis[i]);
					persona.setEstado("ACTIVO");
					personaService.save(persona);
				}

				Usuario usuario = usuarioService.findByUsername(usuarios[i]).orElse(null);
				if (usuario == null) {
					usuario = new Usuario();
					usuario.setUsername(usuarios[i]);
					usuario.setPassword(passwordEncoder.encode(password[i]));
					usuario.setPersona(persona);
					usuario.setRol(rolObjects[i % rolObjects.length]);
					usuario.setEstado("ACTIVO");
					usuarioService.save(usuario);
				}
			}
		};
	}

	/**
	 * Si la base esta completamente vacia (no existe la tabla "rol"), la construye sola:
	 * corre en orden todos los V*.sql de db/reserva (esquema base + migraciones), leidos del
	 * classpath. No hace nada si la tabla ya existe -- nunca toca una base con datos, ni
	 * vuelve a correr algo ya aplicado.
	 *
	 * Por que aqui y no con Hibernate (hbm2ddl.auto=update/create): buena parte de la logica
	 * del sistema (precios, disponibilidad, listados) vive en funciones y un trigger de
	 * PostgreSQL, no en las entidades JPA -- Hibernate no tiene forma de generarlas porque
	 * nunca existieron como codigo Java. Los propios V*.sql ya las traen.
	 */
	private void aplicarEsquemaSiFalta(DataSource dataSource) throws Exception {
		try (Connection con = dataSource.getConnection()) {
			boolean faltaEsquema;
			try (ResultSet rs = con.getMetaData().getTables(null, null, "rol", null)) {
				faltaEsquema = !rs.next();
			}
			if (!faltaEsquema) {
				return;
			}

			logger.warn("Base de datos vacia (no existe la tabla 'rol'): construyendo el esquema desde db/reserva...");

			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			Resource[] scripts = resolver.getResources("classpath:db/reserva/V*.sql");
			Arrays.sort(scripts, Comparator.comparingInt(r -> numeroDeVersion(r.getFilename())));

			// Cada archivo se manda ENTERO en un solo Statement.execute(), sin que Spring lo
			// trocee por ";": ScriptUtils corta las funciones PL/pgSQL a la mitad porque no
			// entiende el $$...$$ de Postgres (hay ";" sueltos dentro del cuerpo). Mandando el
			// texto completo, es el propio Postgres el que separa las sentencias -- igual que
			// hace "psql -f", que es contra lo que estos archivos ya estan probados.
			try (var stmt = con.createStatement()) {
				for (Resource script : scripts) {
					logger.info("Aplicando esquema: {}", script.getFilename());
					String sql = new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
					stmt.execute(sql);
				}
			}

			logger.info("Esquema base aplicado: {} script(s).", scripts.length);
		}
	}

	private static final Pattern VERSION_SCRIPT = Pattern.compile("^V(\\d+)__");

	/** V0, V1, V2... V10, V11 -- nunca alfabetico (si no, V10 quedaria antes que V2). */
	private static int numeroDeVersion(String nombreArchivo) {
		Matcher m = VERSION_SCRIPT.matcher(nombreArchivo == null ? "" : nombreArchivo);
		return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
	}
}