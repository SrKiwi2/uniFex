package com.usic.uniFex.model.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;

import com.usic.uniFex.model.entity.Puesto;

/**
 * Implementación personalizada para asignaciones de vendedores.
 * NO es un JpaRepository: usa EntityManager directamente para evitar
 * el escaneo de Spring Data JPA. La tabla vendedor_puesto no tiene entidad JPA propia.
 */
@Repository
public class VendedorAsignacionDaoImpl implements IVendedorAsignacionDao {

    @PersistenceContext
    private EntityManager em;







    @Override
    public void asignarPuesto(Long usuarioId, Long puestoId, Long adminId) {
        Query q = em.createNativeQuery(
                "INSERT INTO vendedor_puesto (id_usuario, id_puesto, _registro_id_usuario) VALUES (:usuarioId, :puestoId, :adminId) ON CONFLICT DO NOTHING");
        q.setParameter("usuarioId", usuarioId);
        q.setParameter("puestoId", puestoId);
        q.setParameter("adminId", adminId);
        q.executeUpdate();
    }

    @Override
    public void quitarPuesto(Long usuarioId, Long puestoId) {
        Query q = em.createNativeQuery(
                "DELETE FROM vendedor_puesto WHERE id_usuario = :usuarioId AND id_puesto = :puestoId");
        q.setParameter("usuarioId", usuarioId);
        q.setParameter("puestoId", puestoId);
        q.executeUpdate();
    }


    @Override
    @SuppressWarnings("unchecked")
    public List<Long> findPuestoIdsByVendedor(Long usuarioId) {
        return em.createNativeQuery("SELECT id_puesto FROM vendedor_puesto WHERE id_usuario = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Puesto> findPuestosVisiblesParaVendedor(Long usuarioId) {
        // Una sola via: las casetas que se le seleccionaron. Antes habia dos (categoria entera y
        // caseta suelta) y se contradecian: a un vendedor con la categoria asignada se le podian
        // seleccionar 10 casetas y le seguian saliendo todas, porque la categoria pesaba mas.
        return em.createNativeQuery("""
                        SELECT p.* FROM puesto p
                        INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
                        INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id AND vp.id_usuario = :usuarioId
                        WHERE p._estado <> 'X'
                          AND p.estado_puesto <> 'X'
                        ORDER BY c.nombre, length(p.codigo), p.codigo
                        """, Puesto.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    /**
     * Todas las casetas vivas con su categoria y a quien estan habilitadas (o null).
     *
     * Una sola consulta para llenar el modal entero: agrupar por categoria, marcar las del
     * vendedor y senalar las compartidas son decisiones de pantalla, y hacerlas aqui obligaria a
     * ir y volver al servidor por cada una.
     *
     * Desde V32 una caseta puede estar habilitada a VARIOS vendedores, asi que devuelve una fila
     * POR PAREJA (caseta, vendedor) y quien llama las agrupa. Una caseta sin habilitar sigue
     * saliendo una vez, con los dos ultimos campos en null: por eso el LEFT JOIN.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findCatalogoAsignable() {
        return em.createNativeQuery("""
                SELECT p.id, p.codigo, c.id, c.nombre, p.estado_puesto,
                       vp.id_usuario, u.username
                  FROM puesto p
                  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
                  LEFT JOIN vendedor_puesto vp ON vp.id_puesto = p.id
                  LEFT JOIN usuario u ON u.id = vp.id_usuario
                 WHERE p._estado <> 'X' AND p.estado_puesto <> 'X'
                 ORDER BY c.nombre, length(p.codigo), p.codigo, u.username
                """).getResultList();
    }

    /**
     * Deja la asignacion individual de este vendedor EXACTAMENTE en la lista que se pasa.
     *
     * Se hace en dos sentencias (borrar lo que sobra, insertar lo que falta) y no en un ciclo de
     * altas y bajas sueltas: el modal manda la seleccion final de una vez, asi que guardar 40
     * cambios cuesta dos consultas en lugar de 40 peticiones.
     *
     * El {@code ON CONFLICT DO NOTHING} va sobre el indice de PAREJA (V32): lo unico que descarta
     * es volver a insertar una habilitacion que este vendedor ya tenia. Ya NO descarta casetas de
     * otros vendedores —desde V32 se comparten— asi que lo que vuelva en {@code noDisponibles}
     * solo puede ser una caseta que no existe o que esta anulada.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> nombresDeCategoriaPorPuesto(List<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return List.of();
        return em.createNativeQuery("""
                        SELECT p.id, c.nombre
                          FROM puesto p
                          INNER JOIN categoria c ON c.id = p.id_categoria
                         WHERE p.id IN (:ids)
                        """)
                .setParameter("ids", puestoIds)
                .getResultList();
    }

    /**
     * El desglose por categoria de TODOS los vendedores de una vez.
     *
     * Cuenta solo casetas vivas y no bloqueadas, igual que {@code findPuestosVisiblesParaVendedor}:
     * si contara las anuladas, la pantalla de administracion diria "12 de PYMES" y el vendedor
     * veria 10 en su mapa, y el numero que no cuadra siempre parece un fallo del reparto.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> contarPuestosPorCategoriaYVendedor() {
        return em.createNativeQuery("""
                SELECT vp.id_usuario, c.id, c.nombre, COUNT(*)
                  FROM vendedor_puesto vp
                  INNER JOIN puesto p    ON p.id = vp.id_puesto
                                        AND p._estado <> 'X' AND p.estado_puesto <> 'X'
                  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
                 GROUP BY vp.id_usuario, c.id, c.nombre
                 ORDER BY vp.id_usuario, c.nombre
                """).getResultList();
    }

    @Override
    public void borrarAsignacionesDe(Long usuarioId) {
        em.createNativeQuery("DELETE FROM vendedor_puesto WHERE id_usuario = :usuarioId")
                .setParameter("usuarioId", usuarioId).executeUpdate();
    }

    @Override
    public void reemplazarPuestos(Long usuarioId, List<Long> puestoIds, Long adminId) {
        if (puestoIds == null || puestoIds.isEmpty()) {
            em.createNativeQuery("DELETE FROM vendedor_puesto WHERE id_usuario = :usuarioId")
                    .setParameter("usuarioId", usuarioId)
                    .executeUpdate();
            return;
        }
        em.createNativeQuery("""
                DELETE FROM vendedor_puesto
                 WHERE id_usuario = :usuarioId AND id_puesto NOT IN (:ids)
                """)
                .setParameter("usuarioId", usuarioId)
                .setParameter("ids", puestoIds)
                .executeUpdate();

        em.createNativeQuery("""
                INSERT INTO vendedor_puesto (id_usuario, id_puesto, _registro_id_usuario)
                SELECT :usuarioId, p.id, :adminId
                  FROM puesto p
                 WHERE p.id IN (:ids)
                ON CONFLICT DO NOTHING
                """)
                .setParameter("usuarioId", usuarioId)
                .setParameter("adminId", adminId)
                .setParameter("ids", puestoIds)
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Puesto> findPuestosAsignadosByVendedor(Long usuarioId) {
        return em.createNativeQuery("""
                        SELECT p.* FROM puesto p
                        INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id
                        WHERE vp.id_usuario = :usuarioId AND p._estado <> 'X'
                        ORDER BY p.codigo
                        """, Puesto.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    /** ¿Tiene este vendedor alguna asignacion, de cualquiera de las dos clases? */
    @Override
    public boolean tieneAlgunaAsignacion(Long usuarioId) {
        Query q = em.createNativeQuery(
                "SELECT 1 FROM vendedor_puesto WHERE id_usuario = :usuarioId LIMIT 1");
        q.setParameter("usuarioId", usuarioId);
        return !q.getResultList().isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findAsignacionesConVendedor() {
        // Una sola consulta para las ~530 casetas: el mapa la pide UNA vez, no una por caseta.
        // Se excluyen los usuarios dados de baja: su telefono ya no sirve de contacto, y dejar
        // la caseta sin duenio visible es mas honesto que mandar al cliente a quien no esta.
        Query q = em.createNativeQuery("""
                SELECT vp.id_puesto, u.id, pe.nombre, pe.paterno, pe.materno, pe.celular, u.username
                FROM vendedor_puesto vp
                INNER JOIN usuario u ON u.id = vp.id_usuario
                LEFT JOIN persona pe ON pe.id = u.persona_id
                WHERE (u."_estado" IS NULL OR u."_estado" <> 'ELIMINADO')
                """);
        return q.getResultList();
    }

    @Override
    public boolean vendedorTienePuesto(Long usuarioId, Long puestoId) {
        // Dice lo mismo que findPuestosVisiblesParaVendedor. Si difirieran, un vendedor podria
        // vender una caseta que su mapa no le muestra, o al reves.
        Query q = em.createNativeQuery(
                "SELECT 1 FROM vendedor_puesto WHERE id_usuario = :usuarioId AND id_puesto = :puestoId");
        q.setParameter("usuarioId", usuarioId);
        q.setParameter("puestoId", puestoId);
        return !q.getResultList().isEmpty();
    }
}