package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IEntidadService;
import com.usic.uniFex.model.IService.IInscripcionPuestoService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.entity.CategoriaOpcion;
import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.TipoEntidad;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro de una venta de casetas: la operacion central del sistema.
 *
 * Es la version por API de lo que hacia {@code AdminController./guardar} en el sitio
 * Thymeleaf, con tres diferencias que importan:
 *
 *  - **Todo o nada.** Si al confirmar una caseta ya no esta disponible, se lanza una
 *    excepcion y la transaccion revierte la entidad, las personas y la inscripcion. Antes
 *    tambien lo hacia, pero aqui el mensaje dice exactamente que caseta fallo.
 *  - **Se etiqueta la edicion.** El flujo viejo nunca rellenaba {@code id_edicion}, asi que
 *    las ventas quedaban fuera de los listados por edicion.
 *  - **Titular explicito.** El primer responsable es el dueño; el segundo, su acompañante.
 *
 * El invariante anti doble-venta no vive aqui sino en el UPDATE condicional de
 * {@link PuestoReservaService#ocupar}: esta clase solo se encarga de que un fracaso alli
 * deshaga todo lo demas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroVentaService {

    /**
     * Cuantos responsables da de derecho CADA caseta: dos, o sea dos credenciales.
     *
     * Antes el limite era 2 por venta, fijo, sin mirar cuantas casetas se compraban: quien
     * compraba tres tenia derecho a seis personas y el formulario le dejaba meter dos. El
     * limite de una venta es {@link #maxResponsables(int)}.
     */
    public static final int RESPONSABLES_POR_CASETA = 2;

    /** Lo que se cobra por cada responsable POR ENCIMA del derecho (V34). */
    public static final java.math.BigDecimal COSTO_RESPONSABLE_EXTRA = new java.math.BigDecimal("15");

    /** Cuantos responsables admite una venta de {@code casetas} casetas. */
    public static int maxResponsables(int casetas) {
        return Math.max(1, casetas) * RESPONSABLES_POR_CASETA;
    }

    /** Valor de {@code persona._estado} y {@code responsable._estado} para un responsable. */
    private static final String RESPONSABLE = "RESPONSABLE";
    private static final String ACTIVO = "ACTIVO";
    /** Estado inicial de la inscripcion, igual que en el registro Thymeleaf. */
    private static final String PENDIENTE = "PENDIENTE";

    private final IEntidadService entidadService;

    private final CategoriaOpcionService opcionService;
    private final IPersonaService personaService;
    private final IResponsableService responsableService;
    private final IInscripcionService inscripcionService;
    private final IInscripcionPuestoService inscripcionPuestoService;
    private final IPuestoService puestoService;
    private final ITipoEntidadService tipoEntidadService;
    private final IEdicionDao edicionDao;
    private final IInscripcionDao inscripcionDao;
    private final FuncionesInscripcion funciones;
    private final PuestoReservaService reservaService;
    private final PuestoEventPublisher publisher;
    private final FileStorageService storage;
    private final AuditoriaService auditoria;
    private final WhatsAppService whatsApp;
    private final ReciboPdfService reciboPdf;
    private final CredencialImagenService credencialImagen;
    private final CredencialService credencialService;
    @Value("${app.base-url:http://localhost:7676}")
    private String baseUrl;

    // ------------------------------------------------------------------ entrada

    /** Datos de una persona responsable. El primero de la lista es el titular. */
    public record DatosPersona(String nombre, String paterno, String materno, String ci,
                               String correo, String celular) {
    }

    public record NuevaVenta(
            String entidadNombre, String nit, String descripcion, String objeto,
            String representanteLegal, String ciRepresentante, String celularRepresentante,
            Long tipoEntidadId,
            LocalDate fechaInicio, LocalDate fechaFin,
            List<DatosPersona> responsables,
            String entidadBancaria, Long numComprobante, Boolean pagoContado,
            List<Long> puestos,
            /**
             * Que opcion de precio se eligio para cada categoria presente en el carrito:
             * {categoriaId -> opcionId}. Una categoria ausente usa su opcion predeterminada,
             * que es lo que ocurre con las ventas de antes de que existieran las opciones.
             */
            Map<Long, Long> opcionesPorCategoria) {

        /**
         * Sin elegir opciones: cada categoria se cobra con su predeterminada.
         *
         * Existe para que el JSON de un cliente viejo —y las pruebas escritas antes de que
         * hubiera opciones— sigan valiendo tal cual. No elegir es una respuesta legitima: es lo
         * que hace un vendedor cuando la categoria solo tiene una forma de venderse.
         */
        public NuevaVenta(String entidadNombre, String nit, String descripcion, String objeto,
                          String representanteLegal, String ciRepresentante, String celularRepresentante,
                          Long tipoEntidadId, LocalDate fechaInicio, LocalDate fechaFin,
                          List<DatosPersona> responsables, String entidadBancaria,
                          Long numComprobante, Boolean pagoContado, List<Long> puestos) {
            this(entidadNombre, nit, descripcion, objeto, representanteLegal, ciRepresentante,
                 celularRepresentante, tipoEntidadId, fechaInicio, fechaFin, responsables,
                 entidadBancaria, numComprobante, pagoContado, puestos, null);
        }
    }

    /** Resultado del registro. `ok=false` trae el motivo en `mensaje`. */
    public record Resultado(boolean ok, String mensaje, Long inscripcionId,
                            List<Long> puestosOcupados, BigDecimal total) {
        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, null, List.of(), BigDecimal.ZERO);
        }
    }

    /** Se lanza cuando una caseta deja de estar disponible: obliga a revertir la venta entera. */
    public static class CasetaNoDisponibleException extends RuntimeException {
        public CasetaNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }

    /** Una venta y su situacion de pago, para la lista de pendientes del vendedor. */
    public record VentaPendiente(Long id, String entidad, LocalDateTime fechaCompra,
                                 String entidadBancaria, Long numComprobante,
                                 long diasSinComprobante, BigDecimal total) {
    }

    // -------------------------------------------------------------- comprobante

    /**
     * Adjunta el comprobante de pago a una venta ya registrada.
     *
     * Va en una operacion aparte del registro, y no en el mismo formulario, por como se vende
     * en la feria: el vendedor cierra la venta delante del cliente y el comprobante puede
     * llegar despues (transferencia, foto con mala señal). Separarlo permite registrar la
     * venta ya —que es lo que asegura la caseta— y subir la imagen cuando se pueda.
     *
     * Solo puede adjuntarlo quien registro la venta: el id sale del token, nunca del cliente.
     */
    @Transactional
    public Resultado adjuntarComprobante(Long inscripcionId, MultipartFile archivo,
                                         String entidadBancaria, Long numComprobante,
                                         Long usuarioId) {
        return adjuntarComprobante(inscripcionId, archivo, entidadBancaria, numComprobante,
                usuarioId, AuditoriaService.ORIGEN_WEB);
    }

    @Transactional
    public Resultado adjuntarComprobante(Long inscripcionId, MultipartFile archivo,
                                         String entidadBancaria, Long numComprobante,
                                         Long usuarioId, String origen) {
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) return Resultado.error("La venta no existe");
        // El dueño de la venta, o quien acredita. Hace falta lo segundo porque el comprobante
        // aparece muchas veces DELANTE del verificador —el expositor lo lleva en la mano al ir
        // a recoger su credencial— y obligar a que el vendedor lo suba desde otro sitio
        // detendria la cola por algo que se resuelve en el momento.
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !puedeAcreditar()) {
            return Resultado.error("Esta venta no es tuya");
        }
        if (archivo == null || archivo.isEmpty()) {
            return Resultado.error("No se recibio ningun archivo");
        }

        try {
            String nombre = i.getEntidad() != null ? i.getEntidad().getNombre() : ("venta-" + inscripcionId);
            String ruta = storage.save(archivo, FileStorageService.Bucket.COMPROBANTES, nombre);
            i.setImgComprobante(ruta);
            if (entidadBancaria != null && !entidadBancaria.isBlank()) i.setEntidadBancaria(entidadBancaria);
            if (numComprobante != null) i.setNumComprobante(numComprobante);
            i.setModificacion(new Date());
            i.setModificacionIdUsuario(usuarioId);
            inscripcionService.save(i);
            auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, inscripcionId,
                    AuditoriaService.ACCION_COMPROBANTE,
                    "Comprobante Nº " + (numComprobante != null ? numComprobante : "—")
                            + (entidadBancaria != null && !entidadBancaria.isBlank()
                                    ? " · " + entidadBancaria : ""),
                    usuarioId, origen);
            log.info("Comprobante adjuntado a inscripcion={} por usuario={}", inscripcionId, usuarioId);
            return new Resultado(true, "Comprobante adjuntado", inscripcionId, List.of(), BigDecimal.ZERO);
        } catch (IOException e) {
            // El tipo de archivo lo valida FileStorageService: aqui solo se traduce a mensaje.
            log.warn("No se pudo guardar el comprobante de la inscripcion {}: {}", inscripcionId, e.getMessage());
            return Resultado.error("No se pudo guardar el archivo: " + e.getMessage());
        }
    }

    /**
     * Ventas del vendedor que siguen sin comprobante.
     *
     * `diasSinComprobante` es lo que convierte la lista en accionable: no es lo mismo una venta
     * de esta mañana que una de hace una semana. La urgencia se calcula, no se marca a mano.
     */
    @Transactional(readOnly = true)
    public List<VentaPendiente> pendientesDe(Long usuarioId) {
        return inscripcionDao.pendientesDeComprobante(usuarioId).stream()
                .map(i -> new VentaPendiente(
                        i.getId(),
                        i.getEntidad() != null ? i.getEntidad().getNombre() : "(sin entidad)",
                        i.getFechaCompra(),
                        i.getEntidadBancaria(),
                        i.getNumComprobante(),
                        i.getFechaCompra() == null ? 0
                                : ChronoUnit.DAYS.between(i.getFechaCompra().toLocalDate(), LocalDate.now()),
                        i.getInscripcionPuestos().stream()
                                .map(ip -> ip.getCosto() != null ? ip.getCosto() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    // ------------------------------------------------------------------ registro

    @Transactional
    public Resultado registrar(NuevaVenta req, Long usuarioId) {
        return registrar(req, usuarioId, AuditoriaService.ORIGEN_WEB);
    }

    @Transactional
    public Resultado registrar(NuevaVenta req, Long usuarioId, String origen) {
        String error = validar(req);
        if (error != null) return Resultado.error(error);

        TipoEntidad tipo = tipoEntidadService.findById(req.tipoEntidadId());
        if (tipo == null) return Resultado.error("El tipo de entidad no existe");

        Date ahora = new Date();

        Entidad entidad = crearEntidad(req, tipo, ahora, usuarioId);
        crearResponsables(req.responsables(), entidad, ahora, usuarioId);
        Inscripcion inscripcion = crearInscripcion(req, entidad, ahora, usuarioId);

        // Ocupar las casetas. Cada una es un UPDATE condicional: si alguna ya no esta
        // disponible se aborta TODO, porque una venta a medias (entidad creada, casetas no)
        // es peor que no haber empezado.
        List<Long> ocupados = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long puestoId : req.puestos()) {
            if (!reservaService.ocupar(puestoId, usuarioId)) {
                Puesto p = puestoService.findById(puestoId);
                String cual = (p != null)
                        ? (p.getCategoria() != null ? p.getCategoria().getNombre() + " " : "") + p.getCodigo()
                        : ("id " + puestoId);
                throw new CasetaNoDisponibleException(
                        "La caseta " + cual + " ya no esta disponible. Revisa el mapa y vuelve a intentarlo.");
            }
            ocupados.add(puestoId);
            total = total.add(guardarDetalle(puestoId, inscripcion, tipo, ahora, usuarioId,
                                             req.opcionesPorCategoria()));
        }

        // La huella de auditoria viaja en la misma transaccion: si la venta se revierte
        // por cualquier motivo, no queda el rastro de una venta que nunca existio.
        auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, inscripcion.getId(),
                AuditoriaService.ACCION_REGISTRO,
                "Venta registrada: " + (entidad.getNombre() != null ? entidad.getNombre() : "(sin entidad)")
                        + " · " + ocupados.size() + " caseta(s) · total " + total + " Bs",
                usuarioId, origen);

        difundirTrasCommit(ocupados);
        log.info("Venta registrada inscripcion={} casetas={} total={} usuario={}",
                inscripcion.getId(), ocupados.size(), total, usuarioId);

        return new Resultado(true, "Venta registrada", inscripcion.getId(), ocupados, total);
    }

    // ------------------------------------------------------------------ piezas

    /** ¿Quien esta pidiendo esto puede acreditar (administracion o verificador)? */
    private static boolean puedeAcreditar() {
        org.springframework.security.core.Authentication a =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream().map(g -> g.getAuthority()).anyMatch(
                r -> "ROLE_SUPER_USUARIO".equals(r) || "ROLE_ADMINISTRADOR".equals(r)
                        || "ROLE_VERIFICADOR".equals(r));
    }

    private String validar(NuevaVenta req) {
        if (req == null) return "Faltan los datos de la venta";
        if (vacio(req.entidadNombre())) return "El nombre de la entidad es obligatorio";
        if (req.tipoEntidadId() == null) return "Falta el tipo de entidad";
        // El responsable legal es el DUEÑO de la caseta. Antes era opcional y se colaban
        // ventas cuyo unico contacto era el tercero que atiende el puesto: cuando habia que
        // reclamar un pago, no habia a quien llamar. Los responsables de mas abajo son quien
        // ATIENDE, que no es lo mismo.
        if (vacio(req.representanteLegal())) return "Falta el nombre del responsable legal";
        if (vacio(req.ciRepresentante())) return "Falta el C.I. del responsable legal";
        if (vacio(req.celularRepresentante())) return "Falta el celular del responsable legal";
        if (req.puestos() == null || req.puestos().isEmpty()) return "No se selecciono ninguna caseta";
        if (req.puestos().stream().distinct().count() != req.puestos().size()) {
            return "Hay casetas repetidas en la seleccion";
        }
        if (req.responsables() == null || req.responsables().isEmpty()) {
            return "Hace falta al menos el Responsable 1";
        }
        int derecho = maxResponsables(req.puestos().size());
        if (req.responsables().size() > derecho) {
            // Los de mas no se registran aqui: se agregan luego desde la ficha de la venta,
            // donde se cobran y se les pide su comprobante.
            return req.puestos().size() + " caseta(s) dan derecho a " + derecho
                 + " responsables. Los adicionales se agregan desde Mis ventas, con su pago.";
        }
        for (DatosPersona p : req.responsables()) {
            if (vacio(p.nombre())) return "Cada responsable necesita nombre";
            if (vacio(p.ci())) return "Cada responsable necesita C.I.";
        }
        return null;
    }

    private Entidad crearEntidad(NuevaVenta req, TipoEntidad tipo, Date ahora, Long usuarioId) {
        Entidad e = new Entidad();
        e.setNombre(req.entidadNombre().trim());
        e.setNit(req.nit());
        e.setDescripcion(req.descripcion());
        e.setObjeto(req.objeto());
        e.setRepresentanteLegal(req.representanteLegal());
        e.setCiRepresentante(req.ciRepresentante());
        e.setCelularRepresentante(req.celularRepresentante());
        e.setTipoEntidad(tipo);
        e.setEstado(ACTIVO);
        sellar(e, ahora, usuarioId);
        return entidadService.save(e);
    }

    /** El primero de la lista es el Responsable 1 (queda como titular); el segundo, el 2. */
    private void crearResponsables(List<DatosPersona> datos, Entidad entidad, Date ahora, Long usuarioId) {
        for (int i = 0; i < datos.size(); i++) {
            DatosPersona d = datos.get(i);

            Persona persona = new Persona();
            persona.setNombre(d.nombre());
            persona.setPaterno(d.paterno());
            persona.setMaterno(d.materno());
            persona.setCi(d.ci());
            persona.setCorreo(d.correo());
            persona.setCelular(d.celular());
            // Ojo: en `persona` el _estado guarda un TIPO, no ACTIVO/INACTIVO. Marcarla como
            // RESPONSABLE es lo que la mantiene fuera del selector del modulo Usuarios.
            persona.setEstado(RESPONSABLE);
            sellar(persona, ahora, usuarioId);
            personaService.save(persona);

            Responsable r = new Responsable();
            r.setEntidad(entidad);
            r.setPersona(persona);
            r.setEsTitular(i == 0);
            r.setEstado(RESPONSABLE);
            sellar(r, ahora, usuarioId);
            responsableService.save(r);
        }
    }

    private Inscripcion crearInscripcion(NuevaVenta req, Entidad entidad, Date ahora, Long usuarioId) {
        Inscripcion i = new Inscripcion();
        i.setEntidad(entidad);
        i.setFechaCompra(LocalDateTime.now());
        i.setInscripcionEstado(PENDIENTE);
        i.setEstado(ACTIVO);
        if (req.fechaInicio() != null) i.setFechaInicio(java.sql.Date.valueOf(req.fechaInicio()));
        if (req.fechaFin() != null) i.setFechaFin(java.sql.Date.valueOf(req.fechaFin()));
        i.setEntidadBancaria(req.entidadBancaria());
        i.setNumComprobante(req.numComprobante());
        i.setPagoContado(Boolean.TRUE.equals(req.pagoContado()));

        // Sin edicion activa la venta se registra igual: preferible una venta sin etiquetar
        // a bloquear al vendedor delante del cliente. Queda el aviso en el log.
        Edicion edicion = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (edicion == null) log.warn("No hay edicion activa: la inscripcion se registra sin edicion");
        i.setEdicion(edicion);

        sellar(i, ahora, usuarioId);
        return inscripcionService.save(i);
    }

    /**
     * Congela el precio de la caseta en la venta.
     *
     * El costo se copia aqui a proposito: si mañana cambia el precio de la categoria, esta
     * venta debe seguir valiendo lo que valia el dia que se hizo.
     *
     * De donde sale ese costo, en orden: el PRECIO PROPIO de la caseta (V37); la OPCION de
     * precio elegida para la categoria; si no se eligio ninguna, la opcion predeterminada; y si
     * la categoria todavia no tiene opciones, la funcion almacenada de siempre. Ese ultimo
     * escalon no es decorativo: es lo que hace que una base que aun no aplico V33 siga
     * vendiendo al precio correcto.
     *
     * <b>El precio propio va PRIMERO y gana a la opcion.</b> Es lo que se pidio: se crea la
     * categoria con su precio, se colocan sus casetas, y despues algunas valen distinto; hay
     * que poder tocar esas sin alterar las demas. La regla es la mas predecible que hay —el
     * precio de la caseta ES el precio— y se lee igual en el mapa, en el carrito y aqui.
     *
     * Cuando manda el precio propio, la opcion se guarda como NULL <b>a proposito</b>.
     * {@code costo} dice CUANTO se cobro y {@code id_categoria_opcion} dice POR QUE: apuntar a
     * una opcion que no determino el precio seria escribir un porque falso, y dentro de un año
     * alguien leeria "con tarima, 1.000" sobre una opcion de 1.200 y lo tomaria por un error.
     * Un costo con la opcion vacia se lee sin ambiguedad: "esta caseta tenia precio propio".
     *
     * La opcion se valida CONTRA LA BASE (`resolverParaVenta`): el id lo escribe el cliente, y
     * sin esa comprobacion se podria pagar una caseta cara al precio de la opcion barata de otra
     * categoria.
     */
    private BigDecimal guardarDetalle(Long puestoId, Inscripcion inscripcion, TipoEntidad tipo,
                                      Date ahora, Long usuarioId, Map<Long, Long> elegidas) {
        Puesto puesto = puestoService.findById(puestoId);
        Long categoriaId = puesto.getCategoria() != null ? puesto.getCategoria().getId() : null;

        CategoriaOpcion opcion = null;
        if (categoriaId != null) {
            // Se resuelve SIEMPRE, tambien cuando hay precio propio: es lo que valida que el id
            // que mando el cliente sea de esa categoria. Saltarse la comprobacion porque "total,
            // no se va a usar" dejaria pasar sin protestar una opcion ajena.
            opcion = opcionService.resolverParaVenta(
                    categoriaId, elegidas == null ? null : elegidas.get(categoriaId));
        }

        BigDecimal costo = null;
        if (puesto.getPrecio() != null) {
            costo = puesto.getPrecio();
            opcion = null;   // el porque es "precio propio", no una opcion de la categoria
        }
        if (costo == null) costo = opcion != null ? opcion.getPrecio() : null;
        if (costo == null) {
            costo = funciones.obtenerCostoPuesto(tipo.getId(), puesto.getTamano(), categoriaId);
        }
        if (costo == null) costo = BigDecimal.ZERO;

        InscripcionPuesto ip = new InscripcionPuesto();
        ip.setPuesto(puesto);
        ip.setInscripcion(inscripcion);
        ip.setCosto(costo);
        ip.setOpcion(opcion);
        ip.setEstado(ACTIVO);
        sellar(ip, ahora, usuarioId);
        inscripcionPuestoService.save(ip);
        return costo;
    }

    /**
     * Difunde el nuevo estado de las casetas SOLO si la transaccion se confirma.
     *
     * Avisar antes del commit contaria una venta que todavia puede revertirse, y dejaria a
     * los otros vendedores viendo ocupada una caseta que vuelve a estar libre.
     */
    private void difundirTrasCommit(List<Long> ocupados) {
        if (ocupados.isEmpty()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.publicarVarios(ocupados);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publicarVarios(ocupados);
            }
        });
    }

    /**
     * Envía el WhatsApp de bienvenida tras confirmar la transacción.
     *
     * Se hace en afterCommit para no enviar el mensaje si la venta se revierte.
     */
    private void enviarWhatsAppTrasCommit(String celular, String nombreEntidad, Long inscripcionId) {
        if (!whatsApp.habilitado()) {
            log.debug("WhatsApp deshabilitado; no se generan PDFs para inscripcion {}", inscripcionId);
            return;
        }
        if (celular == null || celular.isBlank()) {
            log.debug("Sin celular de responsable legal; no se envía WhatsApp para inscripción {}", inscripcionId);
            return;
        }
        // Normalizar: quitar +, espacios, guiones
        String normalizado = celular.replaceAll("[^0-9]", "");
        // Si empieza con 0 (prefijo nacional), quitarlo para formato internacional
        if (normalizado.startsWith("0")) normalizado = normalizado.substring(1);
        // Asumimos Bolivia (591) si no tiene código de país y tiene 8 dígitos
        if (normalizado.length() == 8) normalizado = "591" + normalizado;

        final String numeroFinal = normalizado;
        Runnable envio = () -> CompletableFuture.runAsync(
                () -> enviarWhatsAppVentaConPdfs(numeroFinal, nombreEntidad, inscripcionId));

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            envio.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envio.run();
            }
        });
    }

    private void enviarWhatsAppVentaConPdfs(String celular, String nombreEntidad, Long inscripcionId) {
        try {
            log.info("[WHATSAPP-VENTA] Inicio envio automatico inscripcion={} celular={} entidad='{}' baseUrl={}",
                    inscripcionId, celular, nombreEntidad, baseUrl);
            ByteArrayOutputStream salidaRecibo = new ByteArrayOutputStream();
            reciboPdf.generarRecibo(inscripcionId, salidaRecibo);
            log.info("[WHATSAPP-VENTA] Recibo generado inscripcion={} bytes={}",
                    inscripcionId, salidaRecibo.size());

            PlantillaCredencial plantilla = PlantillaCredencial.POR_DEFECTO;
            var datosCredenciales = credencialService.porInscripcion(inscripcionId);
            log.info("[WHATSAPP-VENTA] Credenciales encontradas inscripcion={} cantidad={}",
                    inscripcionId, datosCredenciales.size());
            List<byte[]> credenciales = datosCredenciales.stream()
                    .peek(c -> log.info("[WHATSAPP-VENTA] Preparando credencial responsable={} nombre='{}' fotoUrl='{}' conFoto={} conComprobante={} aptaVirtual={}",
                            c.responsableId(), c.nombre(), c.fotoUrl(), c.conFoto(), c.conComprobante(), c.apto(plantilla.id())))
                    .map(c -> credencialImagen.generar(c, plantilla, baseUrl))
                    .toList();
            log.info("[WHATSAPP-VENTA] Credenciales generadas inscripcion={} cantidad={}",
                    inscripcionId, credenciales.size());

            whatsApp.enviarBienvenidaVentaConPdfs(celular, nombreEntidad, inscripcionId,
                    salidaRecibo.toByteArray(), credenciales, baseUrl);
            log.info("[WHATSAPP-VENTA] Fin envio automatico inscripcion={} archivosCredencial={}",
                    inscripcionId, credenciales.size());
        } catch (Exception e) {
            log.warn("No se pudieron generar/enviar PDFs por WhatsApp para inscripcion {}: {}",
                    inscripcionId, e.getMessage());
        }
    }

    /** La auditoria de JPA esta apagada en este proyecto: hay que sellar a mano. */
    private void sellar(com.usic.uniFex.Config.AuditoriaConfig e, Date ahora, Long usuarioId) {
        e.setRegistro(ahora);
        e.setRegistroIdUsuario(usuarioId);
        e.setModificacion(ahora);
        e.setModificacionIdUsuario(usuarioId);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
