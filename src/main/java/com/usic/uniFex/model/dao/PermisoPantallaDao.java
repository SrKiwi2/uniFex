package com.usic.uniFex.model.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las pantallas que ve cada rol. Consultas nativas sobre {@code rol_pantalla}.
 *
 * Sin entidad JPA a proposito: es una tabla de dos columnas que solo se lee entera o se
 * reemplaza entera, y mapearla a una entidad solo añadiria ceremonia.
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

    @Override
    @SuppressWarnings("unchecked")
    public List<String> pantallasDeUsuario(Long usuarioId) {
        Query q = em.createNativeQuery("""
                SELECT rp.pantalla
                  FROM usuario u
                  JOIN rol_pantalla rp ON rp.id_rol = u.rol_id
                 WHERE u.id = :usuario
                """);
        q.setParameter("usuario", usuarioId);
        return q.getResultList();
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
}
