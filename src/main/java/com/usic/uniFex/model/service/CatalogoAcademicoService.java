package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IAreaDao;
import com.usic.uniFex.model.dao.ICarreraDao;
import com.usic.uniFex.model.dto.AreaDTO;
import com.usic.uniFex.model.dto.CarreraDTO;
import com.usic.uniFex.model.entity.Carrera;

import lombok.RequiredArgsConstructor;

/**
 * El catalogo de areas y carreras de la UAP (V35).
 *
 * Es de SOLO LECTURA: las tres areas y sus quince carreras las siembra la migracion, no una
 * pantalla. No hay alta ni baja porque el catalogo no cambia con la feria, cambia con la
 * universidad; el dia que haya una carrera nueva es una linea de SQL, y montar un CRUD para
 * eso seria una pantalla mas que mantener a cambio de nada.
 *
 * Lo consumen tres sitios: el alta de personas, el alta de usuarios y el filtro por area del
 * modulo de vendedores.
 */
@Service
@RequiredArgsConstructor
public class CatalogoAcademicoService {

    private final IAreaDao areaDao;
    private final ICarreraDao carreraDao;

    /** Las areas vivas, cada una con sus carreras dentro. */
    @Transactional(readOnly = true)
    public List<AreaDTO> areasConCarreras() {
        Map<Long, List<CarreraDTO>> porArea = carreras().stream()
                .filter(c -> c.areaId() != null)
                .collect(Collectors.groupingBy(CarreraDTO::areaId));
        return areaDao.listarVivas().stream()
                .map(a -> AreaDTO.de(a, porArea.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    /** Todas las carreras vivas, planas, cada una con la sigla de su area. */
    @Transactional(readOnly = true)
    public List<CarreraDTO> carreras() {
        return CarreraDTO.de(carreraDao.listarVivasConArea());
    }

    /**
     * La carrera por id, o null si no existe o esta de baja.
     *
     * Devuelve null en vez de lanzar porque la carrera es opcional en una persona: "no la
     * encontre" y "no me mandaron ninguna" acaban en el mismo sitio, que es dejar el campo
     * vacio en lugar de tumbar el alta.
     */
    @Transactional(readOnly = true)
    public Carrera buscarViva(Long carreraId) {
        if (carreraId == null) return null;
        return carreraDao.findById(carreraId)
                .filter(c -> !Carrera.REGISTRO_ANULADO.equals(c.getEstado()))
                .orElse(null);
    }
}
