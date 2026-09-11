package com.usic.uniFex.model.dao;

import java.util.List;

import com.usic.uniFex.model.entity.Puesto;

/**
 * Casetas habilitadas a cada vendedor.
 *
 * NO es un JpaRepository: usa EntityManager directamente para evitar el escaneo de Spring Data.
 * La tabla {@code vendedor_puesto} no tiene entidad JPA propia.
 *
 * Hay UNA sola via de habilitacion: las casetas que se le seleccionan. Hubo otra por categoria
 * entera y se contradecian —con la categoria asignada, seleccionarle 10 casetas no servia de nada
 * porque le seguian saliendo todas—, asi que se retiro en V23.
 */
public interface IVendedorAsignacionDao {

    /** Habilita una caseta. Si ya es de otro vendedor no hace nada (indice unico de V20). */
    void asignarPuesto(Long usuarioId, Long puestoId, Long adminId);

    /** Retira una caseta de un vendedor. */
    void quitarPuesto(Long usuarioId, Long puestoId);

    /** Deja las casetas del vendedor exactamente en esa lista, en dos consultas. */
    void reemplazarPuestos(Long usuarioId, List<Long> puestoIds, Long adminId);

    /** Ids de las casetas habilitadas a este vendedor. */
    List<Long> findPuestoIdsByVendedor(Long usuarioId);

    /** Las casetas habilitadas, con sus datos (no solo los ids). */
    List<Puesto> findPuestosAsignadosByVendedor(Long usuarioId);

    /** Las casetas que este vendedor ve en el mapa. Sin habilitaciones, ninguna. */
    List<Puesto> findPuestosVisiblesParaVendedor(Long usuarioId);

    /** true si tiene al menos una caseta habilitada. */
    boolean tieneAlgunaAsignacion(Long usuarioId);

    /** true si esa caseta concreta esta habilitada a ese vendedor. */
    boolean vendedorTienePuesto(Long usuarioId, Long puestoId);

    /**
     * Catalogo para el modal de habilitacion: cada caseta viva con su categoria y su duenio.
     * Columnas: id, codigo, categoriaId, categoriaNombre, estadoPuesto, usuarioId, username.
     */
    List<Object[]> findCatalogoAsignable();

    /** Para un puñado de casetas, el nombre de la categoria de cada una: [puestoId, nombre]. */
    List<Object[]> nombresDeCategoriaPorPuesto(List<Long> puestoIds);

    /** Suelta TODAS las casetas de un usuario. Se usa al darlo de baja. */
    void borrarAsignacionesDe(Long usuarioId);
}
