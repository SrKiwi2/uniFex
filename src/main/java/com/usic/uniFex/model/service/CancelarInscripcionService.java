package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dao.ISolicitudCancelacionDao;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancelacion de una venta/inscripcion.
 *
 * Que hace, y en que orden:
 *  1. Valida el motivo y que la venta exista y no este ya cancelada. Si quien cancela
 *     NO es administracion, exige una solicitud de cancelacion APROBADA vigente (V11):
 *     el vendedor no puede cancelar por su cuenta, solo despues de que administracion
 *     haya aprobado su solicitud. El motivo en ese caso es el de la solicitud.
 *  2. Libera sus casetas (OCUPADO -> LIBRE), cada una con su UPDATE condicional:
 *     una caseta que ya no este ocupada se queda como esta, no se la obliga a nada.
 *  3. Marca la inscripcion con baja logica (_estado = 'X') y guarda el motivo,
 *     quien la cancelo, cuando y desde donde. Los datos de la venta NO se tocan:
 *     entidad, responsables y costos quedan intactos para el historico.
 *  4. Deja la huella de auditoria (accion CANCELACION).
 *  5. Difunde por WebSocket SOLO despues del commit, como toda escritura sobre casetas.
 *
 * El motivo es obligatorio por decision de negocio: una cancelacion sin motivo no
 * permite reconstruir que paso cuando alguien reclame una caseta que "desaparecio".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CancelarInscripcionService {

    /** Valor de la baja logica: el mismo 'X' que usa el resto del sistema. */
    public static final String ANULADA = "X";

    private final IInscripcionDao inscripcionDao;
    private final IPuestoDao puestoDao;
    private final ISolicitudCancelacionDao solicitudDao;
    private final AuditoriaService auditoria;
    private final PuestoEventPublisher publisher;

    /** Peticion de cancelacion: el motivo llega del cliente, la identidad del token. */
    public record Peticion(String motivo) {
    }

    /** Resultado: cuantas casetas se liberaron (para el mapa y el mensaje). */
    public record Resultado(boolean ok, String mensaje, List<Long> puestosLiberados) {
        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, List.of());
        }
    }

    /**
     * Cancela la venta. {@code esAdministracion} decide si el motivo puede venir del
     * cliente (admin) o debe ser el de la solicitud aprobada (vendedor).
     */
    @Transactional
    public Resultado cancelar(Long inscripcionId, String motivo, Long usuarioId, String origen,
                              boolean esAdministracion) {
        Inscripcion i = inscripcionDao.findConTodoPorId(inscripcionId).orElse(null);
        if (i == null) {
            return Resultado.error("La venta no existe");
        }
        if (ANULADA.equals(i.getEstado())) {
            return Resultado.error("La venta ya esta cancelada");
        }

        String motivoReal;
        if (esAdministracion) {
            motivoReal = motivo;
            if (motivoReal == null || motivoReal.isBlank()) {
                return Resultado.error("El motivo de la cancelacion es obligatorio");
            }
            motivoReal = motivoReal.trim();
        } else {
            // El vendedor no cancela por su cuenta: necesita la aprobacion de administracion.
            // El motivo que se guarda es el de la solicitud aprobada, no el que mande el cliente.
            var aprobada = solicitudDao.aprobadaDeInscripcion(inscripcionId);
            if (aprobada.isEmpty()) {
                return Resultado.error("La cancelacion requiere una solicitud aprobada por administracion");
            }
            motivoReal = aprobada.get().getMotivo();
        }

        // 1) Liberar las casetas. Cada una es un UPDATE condicional: si la caseta ya
        // no esta ocupada (se libero antes, se bloqueo), se queda como esta.
        List<Long> liberados = new ArrayList<>();
        for (InscripcionPuesto ip : i.getInscripcionPuestos()) {
            Long puestoId = ip.getPuesto() != null ? ip.getPuesto().getId() : null;
            if (puestoId != null && puestoDao.liberarSiOcupado(puestoId, usuarioId) > 0) {
                liberados.add(puestoId);
            }
        }

        // 2) Marcar la inscripcion cancelada. Solo se tocan los campos de cancelacion:
        // los datos de la venta quedan intactos para el historico.
        Date ahora = new Date();
        i.setEstado(ANULADA);
        i.setMotivoCancelacion(motivoReal);
        i.setFechaCancelacion(java.time.LocalDateTime.now());
        i.setCanceladaPorIdUsuario(usuarioId);
        i.setOrigenCancelacion(origen);
        i.setModificacion(ahora);
        i.setModificacionIdUsuario(usuarioId);
        inscripcionDao.save(i);

        // 3) Auditoria dentro de la misma transaccion: si esto falla, la cancelacion
        // entera revierte y las casetas siguen ocupadas.
        auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, inscripcionId,
                AuditoriaService.ACCION_CANCELACION, motivoReal, usuarioId, origen);

        // 4) Difundir la liberacion solo si la transaccion se confirma.
        difundirTrasCommit(liberados);
        log.info("Inscripcion {} cancelada por usuario={} origen={} casetas liberadas={}",
                inscripcionId, usuarioId, origen, liberados.size());

        return new Resultado(true,
                "Venta cancelada" + (liberados.isEmpty() ? "" : " y " + liberados.size() + " caseta(s) liberada(s)"),
                liberados);
    }

    /** Mismo patron que RegistroVentaService: el broadcast nunca va antes del commit. */
    private void difundirTrasCommit(List<Long> liberados) {
        if (liberados.isEmpty()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.publicarVarios(liberados);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publicarVarios(liberados);
            }
        });
    }
}
