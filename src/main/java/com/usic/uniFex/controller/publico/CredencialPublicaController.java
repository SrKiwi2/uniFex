package com.usic.uniFex.controller.publico;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.service.CredencialService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lo que se ve al escanear el QR de una credencial. <b>Sin login</b>: lo escanea quien
 * controla la puerta, con su propio telefono y sin cuenta en el sistema.
 *
 * <h2>Que protege esto y que no</h2>
 * El codigo va firmado con el secreto del servidor, asi que <b>no se puede inventar</b> uno
 * valido: sin la firma correcta no se llega ni a consultar la base. Lo que NO impide es que
 * quien tenga la credencial (o una foto de ella) abra el enlace, y por decision del cliente
 * esta vista muestra el C.I. completo. Es deliberado: en la puerta hay que poder contrastar el
 * documento. Si algun dia quiere restringirse, el unico cambio es lo que devuelve este metodo.
 *
 * Devuelve 404 tanto si el codigo es falso como si el responsable ya no existe: al de fuera no
 * se le cuenta cual de las dos cosas es.
 */
@RestController
@RequestMapping("/api/publico/credencial")
@RequiredArgsConstructor
@Slf4j
public class CredencialPublicaController {

    private final CredencialService credenciales;

    @GetMapping("/{codigo}")
    public ResponseEntity<Map<String, Object>> ver(@PathVariable String codigo) {
        CredencialDTO c = credenciales.porCodigo(codigo).orElse(null);
        if (c == null) {
            log.info("Credencial consultada con codigo no valido: {}", codigo);
            return ResponseEntity.status(404).body(Map.of(
                    "valida", false,
                    "mensaje", "Esta credencial no es válida"));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valida", true);
        m.put("nombre", c.nombre());
        m.put("ci", c.ci());
        m.put("fotoUrl", c.fotoUrl());
        m.put("esTitular", c.esTitular());
        m.put("entidad", c.entidad());
        m.put("rubro", c.rubro());
        m.put("categoria", c.categoria());
        m.put("casetas", c.casetas());
        // No se expone el id del responsable ni el de la inscripcion: no aportan nada a quien
        // mira la credencial y son las llaves con las que se pediria mas cosas al sistema.
        return ResponseEntity.ok(m);
    }
}
