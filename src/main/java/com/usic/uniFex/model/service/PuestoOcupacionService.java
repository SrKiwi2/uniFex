package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.OcupacionPuestoDTO;

import lombok.RequiredArgsConstructor;

/**
 * Quien tiene cada caseta: el que la esta registrando y el que ya la vendio.
 *
 * Es solo de lectura. La verdad de quien se queda una caseta la decide la maquina de estados
 * del puesto (el UPDATE condicional de {@code IPuestoDao}); esto se limita a ponerle cara y
 * telefono a lo que ya paso, para que el mapa pueda responder "¿y esta quien la vendio?" sin
 * que nadie tenga que llamar a administracion.
 */
@Service
@RequiredArgsConstructor
public class PuestoOcupacionService {

    private final IPuestoDao puestoDao;

    /**
     * Una fila por caseta no libre, con quien la tiene.
     *
     * Las casetas libres y las bloqueadas no aparecen: no las tiene nadie, y mandarlas seria
     * mandar ~300 filas vacias en cada carga del mapa.
     */
    @Transactional(readOnly = true)
    public List<OcupacionPuestoDTO> ocupacionConVendedor() {
        return puestoDao.findOcupacionConVendedor().stream()
                .map(f -> new OcupacionPuestoDTO(
                        numero(f[0]),
                        numero(f[1]),
                        nombreDe(f[2], f[3], f[4], f[6]),
                        limpio(f[5]),
                        limpio(f[7]),
                        fecha(f[8])))
                .filter(o -> o.puestoId() != null && o.vendedorId() != null)
                .toList();
    }

    /**
     * Cuantas casetas lleva vendidas cada vendedor en la edicion activa, por id de usuario.
     *
     * Una sola consulta para los treinta y cinco, no una por vendedor: la pantalla de
     * seguimiento se refresca cada 5 s y una consulta por fila serian treinta y cinco viajes
     * a la base cada vez que alguien la deja abierta.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> vendidasPorVendedor() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] f : puestoDao.contarVendidasPorVendedor()) {
            Long id = numero(f[0]);
            if (id != null) m.put(id, numero(f[1]) == null ? 0L : numero(f[1]));
        }
        return m;
    }

    private static Long numero(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof BigDecimal b) return b.longValue();
        return null;
    }

    private static String limpio(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDateTime fecha(Object v) {
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        if (v instanceof LocalDateTime d) return d;
        return null;
    }

    /**
     * El nombre que se lee en pantalla. Cae al usuario de la cuenta si la persona no tiene
     * nombre cargado: es preferible "jperez" a un hueco, porque un hueco parece un fallo.
     */
    private static String nombreDe(Object nombre, Object paterno, Object materno, Object username) {
        String completo = String.join(" ", Stream.of(nombre, paterno, materno)
                .map(PuestoOcupacionService::limpio)
                .filter(x -> x != null)
                .toList());
        return completo.isBlank() ? limpio(username) : completo;
    }
}
