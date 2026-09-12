package com.usic.uniFex.model.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.dao.IInteresadoStandDao;
import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.InteresadoStand;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Registro de personas interesadas en exponer, capturado desde el formulario público
 * "Quiero exponer" de la SPA. Por ahora solo escribe (sin listado ni panel de gestión:
 * eso queda para cuando exista una vista de administración para estos leads).
 */
@Service
@RequiredArgsConstructor
public class InteresadoStandService {

    private final IInteresadoStandDao interesadoStandDao;
    private final ICategoriaDao categoriaDao;

    @Transactional
    public InteresadoStand registrar(InteresadoStandRequest datos) {
        Categoria categoria = categoriaDao.findById(datos.categoriaId())
                .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada: " + datos.categoriaId()));

        InteresadoStand interesado = new InteresadoStand();
        interesado.setNombreCompleto(datos.nombreCompleto().trim());
        interesado.setCelular(datos.celular().trim());
        interesado.setEmpresa(datos.empresa().trim());
        interesado.setRubro(datos.rubro().trim());
        interesado.setCategoria(categoria);
        return interesadoStandDao.save(interesado);
    }
}
