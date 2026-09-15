package com.usic.uniFex.model.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.dao.IInscripcionPuestoDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agregar un responsable a una venta YA registrada.
 *
 * <h2>El derecho, y lo que hay de mas</h2>
 * Cada caseta da derecho a dos responsables, o sea a dos credenciales: una venta de tres casetas
 * admite seis personas sin pagar nada. Quien pasa de ahi paga
 * {@link RegistroVentaService#COSTO_RESPONSABLE_EXTRA} Bs y tiene que entregar su comprobante.
 *
 * <h2>Por que el comprobante es obligatorio para el extra</h2>
 * Es la misma leccion que la del comprobante de la venta: mientras "pagado" fue algo que se
 * marcaba a mano, se emitieron credenciales de gente que nunca entrego el papel. Aqui el
 * comprobante viaja en la MISMA peticion que crea al responsable, asi que no existe el estado
 * intermedio "responsable extra creado y sin pagar" — no hay forma de olvidarse.
 *
 * El cobro se guarda en el propio responsable (V34) y **no** se suma al total de la inscripcion:
 * es un pago aparte, hecho otro dia y con otro recibo. Sumarlo al total haria que el recibo ya
 * entregado dejara de cuadrar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResponsableExtraService {

    private static final String RESPONSABLE = "RESPONSABLE";

    private final IInscripcionService inscripcionService;
    private final IInscripcionPuestoDao inscripcionPuestoDao;
    private final IResponsableDao responsableDao;
    private final IPersonaService personaService;
    private final IResponsableService responsableService;
    private final FileStorageService almacen;

    /** Datos de la persona que se agrega. */
    public record NuevoResponsable(String nombre, String paterno, String materno, String ci,
                                   String correo, String celular) {
    }

    /** Cuantos caben y cuantos hay: lo que la pantalla necesita para decidir si cobra. */
    public record Cupo(int casetas, int derecho, int registrados, int extras, boolean dentroDelDerecho,
                       BigDecimal costoSiguiente) {
    }

    public record Resultado(boolean ok, String mensaje, Long responsableId, boolean cobrado,
                            BigDecimal monto) {
        static Resultado error(String m) { return new Resultado(false, m, null, false, null); }
    }

    /**
     * Cuantos responsables admite esta venta sin cobrar, y cuantos tiene ya.
     *
     * Se cuentan los responsables VIVOS de la entidad. Uno dado de baja no ocupa sitio: si se
     * quito a alguien del stand, su lugar vuelve a estar disponible.
     */
    @Transactional(readOnly = true)
    public Cupo cupoDe(Long inscripcionId) {
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) return null;
        int casetas = inscripcionPuestoDao.contarActivosDeInscripcion(inscripcionId);
        int derecho = RegistroVentaService.maxResponsables(casetas);
        List<Responsable> vivos = responsableDao.vivosDeEntidad(i.getEntidad().getId());
        int extras = (int) vivos.stream().filter(Responsable::isEsExtra).count();
        boolean dentro = vivos.size() < derecho;
        return new Cupo(casetas, derecho, vivos.size(), extras, dentro,
                dentro ? BigDecimal.ZERO : RegistroVentaService.COSTO_RESPONSABLE_EXTRA);
    }

    /**
     * Agrega el responsable. Si ya se paso del derecho, exige el comprobante y anota el cobro.
     *
     * El comprobante se guarda en disco ANTES de tocar la base: si el archivo falla, no queda
     * un responsable marcado como pagado cuyo papel no existe en ningun sitio.
     */
    @Transactional
    public Resultado agregar(Long inscripcionId, NuevoResponsable datos, MultipartFile comprobante,
                             Long usuarioId) {
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) return Resultado.error("La venta no existe");
        if (datos == null || vacio(datos.nombre())) return Resultado.error("Falta el nombre");
        if (vacio(datos.ci())) return Resultado.error("Falta el C.I.");

        Cupo cupo = cupoDe(inscripcionId);
        boolean cobra = !cupo.dentroDelDerecho();
        if (cobra && (comprobante == null || comprobante.isEmpty())) {
            return Resultado.error("Este responsable pasa de los " + cupo.derecho()
                    + " que dan sus " + cupo.casetas() + " caseta(s): hace falta el comprobante de "
                    + RegistroVentaService.COSTO_RESPONSABLE_EXTRA + " Bs");
        }

        String ruta = null;
        if (comprobante != null && !comprobante.isEmpty()) {
            try {
                ruta = almacen.save(comprobante, FileStorageService.Bucket.COMPROBANTES,
                        i.getEntidad().getNombre() + "-extra");
            } catch (IOException e) {
                log.warn("No se pudo guardar el comprobante del responsable extra: {}", e.getMessage());
                return Resultado.error("No se pudo guardar el comprobante: " + e.getMessage());
            }
        }

        Date ahora = new Date();
        Entidad entidad = i.getEntidad();

        Persona persona = new Persona();
        persona.setNombre(datos.nombre());
        persona.setPaterno(datos.paterno());
        persona.setMaterno(datos.materno());
        persona.setCi(datos.ci());
        persona.setCorreo(datos.correo());
        persona.setCelular(datos.celular());
        // En `persona` el _estado guarda un TIPO, no ACTIVO/INACTIVO: marcarla RESPONSABLE es
        // lo que la mantiene fuera del selector del modulo Usuarios.
        persona.setEstado(RESPONSABLE);
        persona.setRegistro(ahora);
        persona.setRegistroIdUsuario(usuarioId);
        personaService.save(persona);

        Responsable r = new Responsable();
        r.setEntidad(entidad);
        r.setPersona(persona);
        r.setEsTitular(false);
        r.setEsExtra(cobra);
        if (cobra) {
            r.setMontoExtra(RegistroVentaService.COSTO_RESPONSABLE_EXTRA);
            r.setComprobanteExtra(ruta);
        }
        r.setEstado(RESPONSABLE);
        r.setRegistro(ahora);
        r.setRegistroIdUsuario(usuarioId);
        responsableService.save(r);

        log.info("Responsable {} agregado a la inscripcion {} ({})", r.getId(), inscripcionId,
                cobra ? "extra, cobrado" : "dentro del derecho");
        return new Resultado(true,
                cobra ? "Responsable agregado y cobro registrado" : "Responsable agregado",
                r.getId(), cobra, cobra ? RegistroVentaService.COSTO_RESPONSABLE_EXTRA : BigDecimal.ZERO);
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}
