package com.usic.uniFex;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IRolService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.IService.UtilidadesService;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.model.entity.Usuario;

@SpringBootApplication
public class UniFexApplication {

	@Autowired PasswordEncoder passwordEncoder;
	private static final Logger logger = LoggerFactory.getLogger(UniFexApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(UniFexApplication.class, args);
	}

	@Bean
	ApplicationRunner init(IUsuarioService usuarioService, IPersonaService personaService, IRolService rolService, UtilidadesService utilidadesService) {
		
		return args -> {
			
			logger.info("SISTEMA UNIFEXPO...");
			String[] roles = { "SUPER USUARIO", "ADMINISTRADOR" };
			Rol[] rolObjects = new Rol[roles.length];

			for (int i = 0; i < roles.length; i++) {
				Rol rol = rolService.findByNombre(roles[i]).orElse(null);
				if (rol == null) {
					rol = new Rol();
					rol.setNombre(roles[i]);
					rol.setEstado("ACTIVO");
					rolService.save(rol);
				}
				rolObjects[i] = rol;
			}

			String[] cis = { "123456789", "987654321" };
			String[] nombres = { "PRIMER USUARIO", "SEGUNDO USUARIO" };
			String[] usuarios = { "admin1", "admin2" };
			String[] password = { "usuario25$", "usuario&25" };

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
					usuario.setRol(rolObjects[i % roles.length]);
					usuario.setEstado("ACTIVO");
					usuarioService.save(usuario);
				}
			}
		};
	}
}