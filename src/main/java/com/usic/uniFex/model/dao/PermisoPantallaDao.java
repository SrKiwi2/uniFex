package com.usic.uniFex.model.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las pantallas que ve cada rol y cada usuario. Consultas nativas sobre {@code rol_pantalla} y
 * {@code usuario_pantalla} (V49).
 *
 * Sin entidad JPA a proposito: son tablas de dos columnas que solo se leen enteras o se
 * reemplazan enteras, y mapearlas a entidades solo añadiria ceremonia.
 */
@Repository
public class PermisoPantallaDao implements IPermisoPantallaDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> listarTodo() {
        return em.createNativeQuery("""
                SELECT r.id, r.nombre, rp.pantalla
                  FROM rol r
                  LEFT JOIN rol_pantalla rp ON rp.id_rol = r.id
                 WHERE r."_estado" IS NULL OR r."_estado" <> 'ELIMINADO'
                 ORDER BY r.nombre, rp.pantalla
                """).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> pantallasDeRol(Long rolId) {
        Query q = em.createNativeQuery(
                "SELECT pantalla FROM rol_pantalla WHERE id_rol = :rol ORDER BY pantalla");
        q.setParameter("rol", rolId);
        return q.getResultList();
    }

    /** Las de su rol MAS las suyas propias (V49), sin repetir. */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> pantallasDeUsuario(Long usuarioId) {
        Query q = em.createNativeQuery("""
                SELECT rp.pantalla
                  FROM usuario u
                  JOIN rol_pantalla rp ON rp.id_rol = u.rol_id
                 WHERE u.id = :usuario
                UNION
                SELECT up.pantalla
                  FROM usuario_pantalla up
                 WHERE up.id_usuario = :usuario
                """);
        q.setParameter("usuario", usuarioId);
        return q.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> pantallasDelRolDeUsuario(Long usuarioId) {
        Query q = em.createNativeQuery("""
                SELECT rp.pantalla
                  FROM usuario u
                  JOIN rol_pantalla rp ON rp.id_rol = u.rol_id
                 WHERE u.id = :usuario
                 ORDER BY rp.pantalla
                """);
        q.setParameter("usuario", usuarioId);
        return q.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> pantallasPropiasDeUsuario(Long usuarioId) {
        Query q = em.createNativeQuery(
                "SELECT pantalla FROM usuario_pantalla WHERE id_usuario = :usuario ORDER BY pantalla");
        q.setParameter("usuario", usuarioId);
        return q.getResultList();
    }

    /** Nombre legible: su persona, o el login si no tiene. */
    private static final String NOMBRE =
            "coalesce(nullif(btrim(concat_ws(' ', p.nombre, p.paterno, p.materno)), ''), u.username)";

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> listarUsuarios() {
        return em.createNativeQuery("""
                SELECT u.id, u.username, %s AS nombre, r.nombre AS rol, u."_estado",
                       (SELECT count(*) FROM usuario_pantalla up WHERE up.id_usuario = u.id) AS propias
                  FROM usuario u
                  LEFT JOIN persona p ON p.id = u.persona_id
                  LEFT JOIN rol r ON r.id = u.rol_id
                 WHERE u."_estado" IS NULL OR u."_estado" <> 'ELIMINADO'
                 ORDER BY nombre
                """.formatted(NOMBRE)).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object[] usuario(Long usuarioId) {
        List<Object[]> r = em.createNativeQuery("""
                SELECT u.id, u.username, %s AS nombre, r.nombre AS rol
                  FROM usuario u
                  LEFT JOIN persona p ON p.id = u.persona_id
                  LEFT JOIN rol r ON r.id = u.rol_id
                 WHERE u.id = :usuario AND (u."_estado" IS NULL OR u."_estado" <> 'ELIMINADO')
                """.formatted(NOMBRE)).setParameter("usuario", usuarioId).getResultList();
        return r.isEmpty() ? null : r.get(0);
    }

    @Override
    @Transactional
    public void reemplazar(Long rolId, List<String> pantallas, Long adminId) {
        // Se borra y se vuelve a insertar: la pantalla manda la seleccion final, no un delta.
        // Dos consultas pase lo que pase, en vez de una por casilla marcada o desmarcada.
        em.createNativeQuery("DELETE FROM rol_pantalla WHERE id_rol = :rol")
                .setParameter("rol", rolId).executeUpdate();
        for (String p : pantallas) {
            em.createNativeQuery("""
                    INSERT INTO rol_pantalla (id_rol, pantalla, _registro_id_usuario)
                    VALUES (:rol, :pantalla, :admin)
                    ON CONFLICT DO NOTHING
                    """)
                    .setParameter("rol", rolId)
                    .setParameter("pantalla", p)
                    .setParameter("admin", adminId)
                    .executeUpdate();
        }
    }

    @Override
    @Transactional
    public void reemplazarDeUsuario(Long usuarioId, List<String> pantallas, Long adminId) {
        // Igual que por rol: la seleccion final, no un delta.
        em.createNativeQuery("DELETE FROM usuario_pantalla WHERE id_usuario = :usuario")
                .setParameter("usuario", usuarioId).executeUpdate();
        for (String p : pantallas) {
            em.createNativeQuery("""
                    INSERT INTO usuario_pantalla (id_usuario, pantalla, _registro_id_usuario)
                    VALUES (:usuario, :pantalla, :admin)
                    ON CONFLICT DO NOTHING
                    """)
                    .setParameter("usuario", usuarioId)
                    .setParameter("pantalla", p)
                    .setParameter("admin", adminId)
                    .executeUpdate();
        }
    }
}
