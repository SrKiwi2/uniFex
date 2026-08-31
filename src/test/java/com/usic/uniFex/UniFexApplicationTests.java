package com.usic.uniFex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranque del contexto de Spring.
 *
 * Va con el perfil `dev` a proposito. Sin el, el test usaba el perfil por defecto —que es el
 * de PRODUCCION— y moria con "Could not resolve placeholder 'JWT_SECRET'", porque ahi los
 * secretos se leen de variables de entorno sin valor por defecto. Que la suite local dependa
 * de tener exportados los secretos de produccion no tiene sentido, y ademas apuntaria a la
 * base remota.
 */
@SpringBootTest
@ActiveProfiles("dev")
class UniFexApplicationTests {

	@Test
	void contextLoads() {
	}

}
