package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Usuario;

public interface IUsuarioDao extends JpaRepository<Usuario, Long> {
      Optional<Usuario> findByUsername(String username);

      /** Los ids de administracion (roles SUPER USUARIO y ADMINISTRADOR), para notificarles. */
      @Query("select u.id from Usuario u where u.rol.nombre in ('SUPER USUARIO','ADMINISTRADOR') "
              + "and (u.estado is null or u.estado <> 'ELIMINADO')")
      List<Long> idsDeAdministracion();

      /**
       * Los usuarios que muestra la gestion: todos menos los dados de baja logica.
       *
       * El EntityGraph trae persona y rol en la misma consulta. Sin el son dos SELECT extra por
       * fila al construir el UsuarioDTO (rol es LAZY), y encima solo funciona mientras
       * open-in-view siga activo: en el perfil que lo apague, el listado reventaria con
       * LazyInitializationException en vez de ir lento.
       */
      @EntityGraph(attributePaths = { "persona", "rol" })
      @Query("select u from Usuario u where u.estado is null or u.estado <> 'ELIMINADO' order by u.username")
      List<Usuario> listarGestionables();

      /**
       * Cuantos usuarios vivos usan ese nombre de login, sin contar el que se esta editando.
       *
       * Las comprobaciones de unicidad se hacen contando en la BD y no filtrando findAll() en
       * memoria: son 35+ usuarios hoy y cada validacion traia la tabla entera.
       *
       * {@code exceptoId} nunca es null (quien llama pasa -1 cuando no excluye a nadie): un
       * parametro nulo en una comparacion JPQL obliga a Hibernate a adivinar el tipo y falla
       * en tiempo de ejecucion, no de compilacion.
       */
      @Query("select count(u) from Usuario u where upper(u.username) = upper(:username) "
              + "and (u.estado is null or u.estado <> 'ELIMINADO') and u.id <> :exceptoId")
      long contarPorUsername(@Param("username") String username, @Param("exceptoId") Long exceptoId);

      /** Cuantos usuarios vivos cuelgan de esa persona (una persona, un login). */
      @Query("select count(u) from Usuario u where u.persona.id = :personaId "
              + "and (u.estado is null or u.estado <> 'ELIMINADO') and u.id <> :exceptoId")
      long contarPorPersona(@Param("personaId") Long personaId, @Param("exceptoId") Long exceptoId);

      /** Ids de las personas que ya tienen un login vivo (una persona, un usuario). */
      @Query("select u.persona.id from Usuario u where (u.estado is null or u.estado <> 'ELIMINADO') "
              + "and u.persona is not null")
      List<Long> idsDePersonasConUsuario();

      /** Cuantos usuarios vivos tiene un rol. Un rol con usuarios no se puede eliminar. */
      @Query("select count(u) from Usuario u where u.rol.id = :rolId "
              + "and (u.estado is null or u.estado <> 'ELIMINADO')")
      long contarPorRol(@Param("rolId") Long rolId);

      /** Cuantos usuarios por rol, para el listado del modulo de roles (una sola consulta). */
      @Query("select u.rol.id, count(u) from Usuario u "
              + "where (u.estado is null or u.estado <> 'ELIMINADO') group by u.rol.id")
      List<Object[]> contarUsuariosPorRol();

      /** Usuarios vivos con un rol dado (por nombre del rol). */
      @Query("select u from Usuario u join u.rol r where r.nombre = :rolNombre and (u.estado is null or u.estado <> 'ELIMINADO')")
      List<Usuario> findByRolNombre(@Param("rolNombre") String rolNombre);

      /**
       * Usuarios que pueden entrar HOY con ese rol, sin contar a uno dado. Sirve para no dejar
       * el sistema sin ningun SUPER USUARIO activo, que es un estado del que no se sale desde
       * la propia aplicacion: el modulo que arregla los roles exige ese rol para entrar.
       */
      @Query("select count(u) from Usuario u where u.rol.nombre = :rol "
              + "and u.estado = 'ACTIVO' and u.id <> :exceptoId")
      long contarActivosPorRol(@Param("rol") String rol, @Param("exceptoId") Long exceptoId);
}
