package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.usic.uniFex.model.dao.IInstanciaWhatsAppDao;
import com.usic.uniFex.model.dto.InstanciaWhatsAppDTO;
import com.usic.uniFex.model.entity.InstanciaWhatsApp;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;

@Service
@RequiredArgsConstructor
public class InstanciaWhatsAppService {
    private final IInstanciaWhatsAppDao dao;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<InstanciaWhatsAppDTO> listar() {
        return dao.findAllByOrderByActivaDescIdAsc().stream().map(InstanciaWhatsAppDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public Optional<InstanciaWhatsApp> activa() {
        return dao.findByActivaTrue();
    }

    @Transactional
    public InstanciaWhatsAppDTO guardar(Long id, String nombre, String urlApi, String instancia, String claveApi) {
        bloquearEscrituras();
        InstanciaWhatsApp entidad = id == null ? new InstanciaWhatsApp() : buscar(id);
        entidad.setNombre(texto(nombre, 120, "El nombre"));
        entidad.setUrlApi(normalizarUrl(urlApi));
        entidad.setInstancia(texto(instancia, 200, "La instancia"));
        if (id == null || (claveApi != null && !claveApi.isBlank())) {
            String clave = texto(claveApi, 500, "La clave API");
            if (clave.chars().anyMatch(c -> c < 32 || c > 126)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave API contiene caracteres no admitidos.");
            }
            entidad.setClaveApi(clave);
        }
        if (id == null) entidad.setActiva(dao.count() == 0);
        return InstanciaWhatsAppDTO.de(dao.saveAndFlush(entidad));
    }

    @Transactional
    public List<InstanciaWhatsAppDTO> cambiarEstado(Long id, boolean activa) {
        bloquearEscrituras();
        InstanciaWhatsApp elegida = buscar(id);
        if (!activa && elegida.isActiva()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Debe quedar una instancia activa. Activa otra para reemplazar esta.");
        }
        if (activa && !elegida.isActiva()) {
            dao.findByActivaTrue().ifPresent(anterior -> {
                anterior.setActiva(false);
                dao.saveAndFlush(anterior);
            });
            elegida.setActiva(true);
            dao.saveAndFlush(elegida);
        }
        return listar();
    }

    /** Puente de migracion: nunca sobrescribe una configuracion administrada desde la SPA. */
    @Transactional
    public void importarInicial(String urlApi, String instancia, String claveApi) {
        bloquearEscrituras();
        if (dao.count() == 0) guardar(null, "Instancia inicial", urlApi, instancia, claveApi);
    }

    private void bloquearEscrituras() {
        // Tambien serializa dos altas cuando la tabla aun esta vacia. No bloquea los envios.
        jdbc.execute("LOCK TABLE instancia_whatsapp IN SHARE ROW EXCLUSIVE MODE");
    }

    private InstanciaWhatsApp buscar(Long id) {
        return dao.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "La instancia no existe."));
    }

    private static String texto(String valor, int limite, String campo) {
        if (valor == null || valor.isBlank() || valor.trim().length() > limite) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    campo + " es obligatorio y admite hasta " + limite + " caracteres.");
        }
        return valor.trim();
    }

    private static String normalizarUrl(String valor) {
        String texto = texto(valor, 500, "La URL de la API");
        HttpUrl url = HttpUrl.parse(texto);
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty()
                || url.query() != null || url.fragment() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Usa una URL HTTP o HTTPS sin credenciales, parametros ni fragmentos.");
        }
        String normalizada = url.toString();
        if (!normalizada.endsWith("/")) normalizada += "/";
        return texto(normalizada, 500, "La URL de la API");
    }
}
