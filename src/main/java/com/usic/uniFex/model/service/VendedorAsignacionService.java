package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dao.IVendedorAsignacionDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.dto.AsignacionPuestoDTO;
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
 * Una caseta habilitada es de UN solo vendedor (indice unico de V20), y **sin habilitaciones no
 * ve ninguna**: nadie vende hasta que administracion se las selecciona.
 */
@Service
@RequiredArgsConstructor
public class VendedorAsignacionService {

    private static final Logger logger = LoggerFactory.getLogger(VendedorAsignacionService.class);

    private final IVendedorAsignacionDao dao;
    private final IUsuarioDao usuarioDao;
    private final NotificacionService notificaciones;

    // ===== CASETAS HABILITADAS =====



    @Transactional
    public void quitarPuesto(Long vendedorId, Long puestoId) {
        dao.quitarPuesto(vendedorId, puestoId);
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

    /** Una caseta tal como la ve el modal de asignacion. */
    public record CasetaAsignable(Long id, String codigo, Long categoriaId, String categoria,
                                  String estado, Long asignadoAId, String asignadoA) {
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
        return dao.findCatalogoAsignable().stream()
                .map(x -> new CasetaAsignable(
                        numero(x[0]), texto(x[1]), numero(x[2]), texto(x[3]), texto(x[4]),
                        numero(x[5]), texto(x[6])))
                .toList();
    }

    /**
     * Guarda de una vez la seleccion completa de casetas de un vendedor.
     *
     * Recibe el estado final, no una lista de altas y bajas: el modal ya sabe que quiere, y
     * mandarlo entero evita el ir y venir de una peticion por clic. El servicio calcula el
     * cambio para poder informarlo.
     *
     * Una caseta que entretanto tomo otro vendedor no se roba: se salta y vuelve en
     * {@code noDisponibles}, para que la pantalla lo diga en vez de mentir con un exito.
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
     * Desde V20 una caseta asignada es exclusiva, asi que las de un vendedor eliminado quedaban
     * bloqueadas para siempre, invisibles para todos y sin pantalla desde la que soltarlas.
     * Las asignaciones son configuracion, no historia: quien vendio que vive en la inscripcion.
     */
    @Transactional
    public void liberarAsignacionesDe(Long vendedorId) {
        dao.borrarAsignacionesDe(vendedorId);
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

    @Transactional(readOnly = true)
    public List<Usuario> listarVendedores() {
        return usuarioDao.findByRolNombre("ADMINISTRATIVO");
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
