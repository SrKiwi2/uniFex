package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dao.IVendedorAsignacionDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.dto.AsignacionPuestoDTO;
import com.usic.uniFex.model.dto.VendedorDTO;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Que casetas tiene habilitadas cada vendedor (rol ADMINISTRATIVO).
 *
 * **Una sola via de habilitacion: las casetas que se le seleccionan.** Hubo otra por categoria
 * entera, y las dos se contradecian: a un vendedor con la categoria asignada se le podian
 * seleccionar 10 casetas y le seguian saliendo TODAS, porque la categoria pesaba mas. Tambien
 * hubo un "cupo" aparte, que era una tercera forma de decir lo mismo y podia contradecir a la
 * seleccion. Las dos se retiraron en V23: si se le habilitan 10 casetas, ve 10 y vende 10.
 *
 * **Una caseta puede estar habilitada a VARIOS vendedores** (V32). Lo contrario fue la regla
 * hasta V20 y estorbaba: en la feria varios vendedores atienden el mismo sector, y quien cierra
 * el trato primero registra. Que no se venda dos veces NO lo garantiza esta tabla, lo garantiza
 * la maquina de estados de la caseta: el primero que reserva se lleva la fila y al resto les sale
 * ocupada. Habilitar es dar permiso para intentarlo, no repartir propiedad.
 *
 * **Sin habilitaciones no ve ninguna**: nadie vende hasta que administracion se las selecciona.
 */
@Service
@RequiredArgsConstructor
public class VendedorAsignacionService {

    private static final Logger logger = LoggerFactory.getLogger(VendedorAsignacionService.class);

    private final IVendedorAsignacionDao dao;
    private final PuestoEventPublisher publisher;
    private final IUsuarioDao usuarioDao;
    private final NotificacionService notificaciones;

    // ===== CASETAS HABILITADAS =====



    @Transactional
    public void quitarPuesto(Long vendedorId, Long puestoId) {
        dao.quitarPuesto(vendedorId, puestoId);
        difundirCambios(List.of(puestoId));
    }

    @Transactional(readOnly = true)
    public Set<Long> getPuestoIdsDeVendedor(Long vendedorId) {
        return dao.findPuestoIdsByVendedor(vendedorId).stream().collect(Collectors.toSet());
    }

    // ===== MAPA FILTRADO =====

    /**
     * Las casetas que este vendedor ve en el mapa: solo las suyas.
     *
     * **Sin asignaciones, la lista viene vacia.** Nadie vende hasta que administracion le habilita
     * una categoria o unas casetas. La pantalla lo dice con todas las letras en vez de mostrar un
     * mapa vacio sin explicacion.
     *
     * Comparte la regla con {@link #puedeVender} a proposito: si difirieran, un vendedor podria
     * vender algo que su mapa no le muestra, o al reves.
     */
    @Transactional(readOnly = true)
    public List<Puesto> getPuestosVisiblesParaVendedor(Long vendedorId) {
        return dao.findPuestosVisiblesParaVendedor(vendedorId);
    }

    /** Un vendedor que lleva una caseta, como lo lista el modal. */
    public record Habilitado(Long id, String username) {
    }

    /**
     * Una caseta tal como la ve el modal de habilitacion.
     *
     * `habilitados` es una LISTA, no un duenio suelto: desde V32 la misma caseta puede llevarla
     * mas de un vendedor. Vacia significa que aun no la lleva nadie.
     */
    public record CasetaAsignable(Long id, String codigo, Long categoriaId, String categoria,
                                  String estado, List<Habilitado> habilitados) {
    }

    /** Cuantas casetas se sumaron y cuantas se quitaron de UNA categoria. */
    public record CambioPorCategoria(String categoria, int sumadas, int quitadas) {
    }

    /**
     * Lo que cambio al guardar. Lleva el desglose por categoria porque el aviso que recibe el
     * vendedor tiene que decir de QUE es lo que le dieron o le quitaron: "2 casetas de PYMES"
     * significa algo, "2 casetas" no.
     */
    public record ResultadoAsignacion(int asignadas, int quitadas, List<Long> noDisponibles,
                                      List<CambioPorCategoria> porCategoria) {
    }

    /**
     * Catalogo completo para el modal: todas las casetas vivas, con su categoria y a quien estan
     * asignadas. Va en una sola peticion porque agrupar, filtrar y marcar son decisiones de
     * pantalla; pedirle al servidor una consulta por categoria seria mas lento y mas fragil.
     */
    @Transactional(readOnly = true)
    public List<CasetaAsignable> catalogoAsignable() {
        // La consulta trae una fila por PAREJA (caseta, vendedor), asi que una caseta compartida
        // llega repetida. Se agrupa aqui conservando el orden de la consulta —por categoria y
        // codigo— que es el orden en que la pantalla las pinta.
        Map<Long, CasetaAsignable> porCaseta = new java.util.LinkedHashMap<>();
        for (Object[] x : dao.findCatalogoAsignable()) {
            Long id = numero(x[0]);
            CasetaAsignable caseta = porCaseta.computeIfAbsent(id, k -> new CasetaAsignable(
                    id, texto(x[1]), numero(x[2]), texto(x[3]), texto(x[4]),
                    new java.util.ArrayList<>()));
            Long vendedorId = numero(x[5]);
            if (vendedorId != null) caseta.habilitados().add(new Habilitado(vendedorId, texto(x[6])));
        }
        return List.copyOf(porCaseta.values());
    }

    /**
     * Guarda de una vez la seleccion completa de casetas de un vendedor.
     *
     * Recibe el estado final, no una lista de altas y bajas: el modal ya sabe que quiere, y
     * mandarlo entero evita el ir y venir de una peticion por clic. El servicio calcula el
     * cambio para poder informarlo.
     *
     * {@code noDisponibles} ya no puede traer "es de otro vendedor": desde V32 las casetas se
     * comparten y no hay nada que robar. Lo que trae es una caseta que se pidio y no quedo: no
     * existe, o esta anulada. Se informa igual, porque un guardado que dice "listo" habiendo
     * ignorado tres casetas es peor que uno que lo cuenta.
     */
    @Transactional
    public ResultadoAsignacion reemplazarPuestos(Long vendedorId, List<Long> puestoIds, Long adminId) {
        validarVendedor(vendedorId);
        List<Long> pedidos = puestoIds == null ? List.of() : puestoIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Set<Long> antes = getPuestoIdsDeVendedor(vendedorId);

        dao.reemplazarPuestos(vendedorId, pedidos, adminId);

        Set<Long> despues = getPuestoIdsDeVendedor(vendedorId);
        List<Long> noDisponibles = pedidos.stream().filter(id -> !despues.contains(id)).toList();
        List<Long> sumadas = despues.stream().filter(id -> !antes.contains(id)).toList();
        List<Long> quitadas = antes.stream().filter(id -> !despues.contains(id)).toList();

        logger.info("Vendedor {}: {} caseta(s) asignadas, {} quitadas, {} no disponibles (admin {})",
                vendedorId, sumadas.size(), quitadas.size(), noDisponibles.size(), adminId);

        List<CambioPorCategoria> porCategoria = desglosarPorCategoria(sumadas, quitadas);
        if (!porCategoria.isEmpty()) {
            avisarAlVendedor(vendedorId, redactar(porCategoria));
        }
        // Que los mapas abiertos —web y APK— se enteren sin recargar nada.
        difundirCambios(Stream.concat(sumadas.stream(), quitadas.stream()).distinct().toList());
        return new ResultadoAsignacion(sumadas.size(), quitadas.size(), noDisponibles, porCategoria);
    }

    /**
     * Manda el aviso al vendedor. El tipo ASIGNACION_CAMBIADA es ademas la señal con la que su
     * mapa se recarga solo, en la web y en el APK: sin eso, seguiria viendo la lista vieja hasta
     * que cerrara y volviera a abrir, que es justo lo que hay que evitar.
     *
     * Nunca tumba la operacion: la asignacion ya esta guardada, y quedarse sin aviso es molesto
     * pero no es motivo para deshacerla.
     */
    private void avisarAlVendedor(Long vendedorId, String mensaje) {
        try {
            notificaciones.notificar(vendedorId, NotificacionService.TIPO_ASIGNACION,
                    "Cambió lo que puedes vender", mensaje, null, null);
        } catch (Exception e) {
            logger.warn("No se pudo avisar al vendedor {} del cambio de asignacion: {}",
                    vendedorId, e.getMessage());
        }
    }

    /** "Se te asignaron 2 casetas de PYMES. Se te quitaron 1 caseta de ARTESANIA." */
    private String redactar(List<CambioPorCategoria> cambios) {
        StringBuilder sb = new StringBuilder();
        for (CambioPorCategoria c : cambios) {
            if (c.sumadas() > 0) {
                sb.append("Se te asignaron ").append(c.sumadas()).append(casetas(c.sumadas()))
                  .append(" de ").append(c.categoria()).append(". ");
            }
            if (c.quitadas() > 0) {
                sb.append("Se te quitaron ").append(c.quitadas()).append(casetas(c.quitadas()))
                  .append(" de ").append(c.categoria()).append(". ");
            }
        }
        return sb.toString().trim();
    }

    private static String casetas(int n) { return n == 1 ? " caseta" : " casetas"; }


    /** Agrupa las casetas que entraron y salieron por categoria, para poder nombrarlas en el aviso. */
    private List<CambioPorCategoria> desglosarPorCategoria(List<Long> sumadas, List<Long> quitadas) {
        List<Long> todas = new java.util.ArrayList<>(sumadas);
        todas.addAll(quitadas);
        if (todas.isEmpty()) return List.of();

        java.util.Map<Long, String> categoriaDe = new java.util.HashMap<>();
        for (Object[] fila : dao.nombresDeCategoriaPorPuesto(todas)) {
            categoriaDe.put(numero(fila[0]), texto(fila[1]));
        }
        java.util.Map<String, int[]> acumulado = new java.util.LinkedHashMap<>();
        for (Long id : sumadas) {
            acumulado.computeIfAbsent(categoriaDe.getOrDefault(id, "(sin categoria)"), k -> new int[2])[0]++;
        }
        for (Long id : quitadas) {
            acumulado.computeIfAbsent(categoriaDe.getOrDefault(id, "(sin categoria)"), k -> new int[2])[1]++;
        }
        return acumulado.entrySet().stream()
                .map(e -> new CambioPorCategoria(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private static Long numero(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private static String texto(Object o) { return o == null ? null : o.toString(); }

    /** Las casetas sueltas que tiene asignadas, para mostrarlas en la pantalla de Vendedores. */
    @Transactional(readOnly = true)
    public List<Puesto> getPuestosAsignados(Long vendedorId) {
        return dao.findPuestosAsignadosByVendedor(vendedorId);
    }

    @Transactional(readOnly = true)
    public boolean vendedorTienePuesto(Long vendedorId, Long puestoId) {
        return dao.vendedorTienePuesto(vendedorId, puestoId);
    }

    /**
     * ¿Puede este vendedor vender esta caseta? Misma regla que el mapa: sin asignaciones, todas.
     *
     * Existe porque esconder una caseta en el mapa NO impide venderla. Las peticiones llevan el id
     * de la caseta, y un cliente puede mandar cualquiera; sin esta comprobacion en el servidor, la
     * asignacion seria solo decoracion de pantalla.
     */
    @Transactional(readOnly = true)
    public boolean puedeVender(Long vendedorId, Long puestoId) {
        return dao.vendedorTienePuesto(vendedorId, puestoId);
    }

    /**
     * De una lista de casetas, las que este vendedor NO tiene permitidas. Devuelve vacio para
     * quien no es vendedor: administracion vende cualquier caseta.
     */
    @Transactional(readOnly = true)
    public List<Long> casetasNoPermitidas(Long vendedorId, List<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return List.of();
        return puestoIds.stream().filter(id -> !dao.vendedorTienePuesto(vendedorId, id)).toList();
    }

    /**
     * Suelta todo lo que tenia asignado un vendedor. La llama la baja del usuario.
     *
     * Hace falta porque la baja es LOGICA: la fila del usuario se queda, y sus casetas con ella.
     * Mientras una caseta fue de un solo vendedor (V20), las de uno eliminado quedaban bloqueadas
     * para siempre, invisibles para todos y sin pantalla desde la que soltarlas. Desde V32 ya no
     * bloquean a nadie, pero siguen sobrando: su nombre y su telefono saldrian en el mapa como
     * contacto de alguien que ya no esta.
     * Las asignaciones son configuracion, no historia: quien vendio que vive en la inscripcion.
     */
    @Transactional
    public void liberarAsignacionesDe(Long vendedorId) {
        List<Long> sueltas = List.copyOf(getPuestoIdsDeVendedor(vendedorId));
        dao.borrarAsignacionesDe(vendedorId);
        difundirCambios(sueltas);
    }

    // ===== VALIDACIONES =====

    private void validarVendedor(Long vendedorId) {
        Usuario u = usuarioDao.findById(vendedorId).orElse(null);
        if (u == null) throw new IllegalArgumentException("El vendedor no existe");
        if (u.getRol() == null || !"ADMINISTRATIVO".equals(u.getRol().getNombre())) {
            throw new IllegalArgumentException("El usuario no es un vendedor (rol ADMINISTRATIVO)");
        }
        if ("X".equals(u.getEstado()) || "ELIMINADO".equals(u.getEstado())) {
            throw new IllegalArgumentException("El vendedor está inactivo o eliminado");
        }
    }

    // ===== LISTADO DE VENDEDORES =====

    /**
     * Los vendedores para la pantalla de administracion, cada uno con su carrera, su area y
     * el desglose de casetas por categoria.
     *
     * Son DOS consultas para toda la tabla, no dos por fila: la de usuarios ya trae persona,
     * carrera y area con {@code join fetch}, y el desglose de los 35 vendedores viene de una
     * sola agregacion que se reparte aqui en memoria.
     */
    @Transactional(readOnly = true)
    public List<VendedorDTO> listarVendedores() {
        Map<Long, List<VendedorDTO.CategoriaAsignada>> porVendedor = dao
                .contarPuestosPorCategoriaYVendedor().stream()
                .collect(Collectors.groupingBy(
                        f -> numero(f[0]),
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(
                                f -> new VendedorDTO.CategoriaAsignada(
                                        numero(f[1]), texto(f[2]), ((Number) f[3]).intValue()),
                                Collectors.toList())));

        return usuarioDao.findByRolNombre("ADMINISTRATIVO").stream()
                .map(u -> VendedorDTO.de(u, porVendedor.getOrDefault(u.getId(), List.of())))
                .toList();
    }
    /**
     * Todas las asignaciones con el contacto de su vendedor, para el mapa.
     *
     * Lo ve cualquier vendedor autenticado, y es deliberado: el mapa ahora ensena TODAS las
     * casetas —las ajenas en gris— y al tocar una ajena tiene que poder decir a quien derivar
     * al cliente. Sin el telefono, ensenarlas solo serviria para frustrar.
     */
    @Transactional(readOnly = true)
    public List<AsignacionPuestoDTO> asignacionesConVendedor() {
        return dao.findAsignacionesConVendedor().stream()
                .map(f -> new AsignacionPuestoDTO(
                        numero(f[0]),
                        numero(f[1]),
                        nombreDe(f[2], f[3], f[4], f[6]),
                        limpio(f[5])))
                .toList();
    }

    /**
     * Difunde a todos los mapas abiertos como queda la asignacion de unas casetas.
     *
     * Dos decisiones que importan:
     *
     * 1. Se RELEE el estado real de esas casetas en vez de deducirlo de lo que se acaba de
     *    hacer. Una caseta puede estar habilitada a varios vendedores (V32), asi que
     *    "se la quite a A" no significa "se quedo sin nadie": puede seguir llevandola B.
     *    Deducirlo pintaria gris una caseta que el otro si puede vender.
     *
     * 2. Se manda la lista COMPLETA de habilitados de cada caseta tocada, no el cambio suelto.
     *    Un mensaje "A ya no la lleva" obligaria al cliente a recomponer la lista y a acertar
     *    con el orden de llegada; mandando quien la lleva AHORA, el cliente solo reemplaza.
     *    Una caseta que se quedo sin nadie viaja como una fila con los campos en null, que es
     *    la forma que el cliente ya entiende como "borra lo que tengas de esta".
     *
     * 3. Se publica DESPUES del commit. Publicar dentro de la transaccion y que esta falle
     *    despues dejaria a los moviles mostrando una asignacion que nunca existio. Es la
     *    misma regla que sigue el estado de las casetas.
     */
    private void difundirCambios(List<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return;

        Set<Long> tocadas = Set.copyOf(puestoIds);
        Map<Long, List<AsignacionPuestoDTO>> vigentes = asignacionesConVendedor().stream()
                .filter(a -> tocadas.contains(a.puestoId()))
                .collect(Collectors.groupingBy(AsignacionPuestoDTO::puestoId));

        List<AsignacionPuestoDTO> cambios = puestoIds.stream().distinct()
                .flatMap(id -> vigentes.containsKey(id)
                        ? vigentes.get(id).stream()
                        : Stream.of(new AsignacionPuestoDTO(id, null, null, null)))
                .toList();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publicarAsignaciones(cambios);
                }
            });
        } else {
            publisher.publicarAsignaciones(cambios);
        }
    }

    /** Como {@link #texto}, pero recorta y convierte el vacio en null. */
    private static String limpio(Object o) {
        String t = o == null ? "" : o.toString().trim();
        return t.isEmpty() ? null : t;
    }

    /** Nombre y apellidos; si la persona no tiene nombre, al menos el usuario. */
    private static String nombreDe(Object nombre, Object paterno, Object materno, Object username) {
        String completo = Stream.of(nombre, paterno, materno)
                .map(VendedorAsignacionService::limpio)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return completo.isEmpty() ? limpio(username) : completo;
    }
}
