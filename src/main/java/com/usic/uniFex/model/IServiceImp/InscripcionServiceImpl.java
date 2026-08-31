package com.usic.uniFex.model.IServiceImp;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dao.IAdministrativoDao;
import com.usic.uniFex.model.dao.IAuditoriaDao;
import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.dto.InscripcionDetalleDTO;
import com.usic.uniFex.model.dto.InscripcionListadoDTO;
import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.Usuario;

@Service
public class InscripcionServiceImpl implements IInscripcionService{

    private final IAdministrativoDao IAdministrativoDao;
    private final IAuditoriaDao auditoriaDao;
    private final IResponsableDao responsableDao;

    @Autowired
    private IInscripcionDao inscripcionDao;

    InscripcionServiceImpl(IAdministrativoDao IAdministrativoDao, IAuditoriaDao auditoriaDao,
                           IResponsableDao responsableDao) {
        this.IAdministrativoDao = IAdministrativoDao;
        this.auditoriaDao = auditoriaDao;
        this.responsableDao = responsableDao;
    }

    @Override
    public List<Inscripcion> findAll() {
        // TODO Auto-generated method stub
        return inscripcionDao.findAll();
    }

    @Override
    public Inscripcion findById(Long idEntidad) {
        // TODO Auto-generated method stub
        return inscripcionDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Inscripcion save(Inscripcion entidad) {
        // TODO Auto-generated method stub
        return inscripcionDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        // TODO Auto-generated method stub
        inscripcionDao.deleteById(idEntidad);
    }

    @Override
    public List<ResumenCategoriaView> resumenPorCategoria() {
        return inscripcionDao.resumenPorCategoria();
    }

    @Override
    public List<ResumenEntidadView> resumenPorEntidad() {
        return inscripcionDao.resumenPorEntidad();
    }

    @Override
    public com.usic.uniFex.model.dto.ResumenGeneralView resumenGeneral() {
        return inscripcionDao.resumenGeneral();
    }

    @Override
    public List<InscripcionListadoDTO> listarParaTabla() {
        return listarParaTabla(false);
    }

    /**
     * @param canceladas false = las activas (lo que ve la SPA por defecto);
     *                   true  = el historico de canceladas (baja logica).
     */
    @Override
    public List<InscripcionListadoDTO> listarParaTabla(boolean canceladas) {
        List<Inscripcion> ins = canceladas ? inscripcionDao.findAllCanceladas()
                : inscripcionDao.findAllConTodo();
        return ins.stream().map(this::aListado).collect(Collectors.toList());
    }

    /**
     * Detalle completo para el modulo de Inscripciones: datos de la venta, responsables,
     * casetas con costo, datos de cancelacion y la traza de auditoria.
     *
     * @return null si la inscripcion no existe.
     */
    @Override
    public InscripcionDetalleDTO detalleParaTabla(Long id) {
        Inscripcion i = inscripcionDao.findConTodoPorId(id).orElse(null);
        if (i == null) return null;

        var entidad = i.getEntidad();
        var tipo = (entidad != null && entidad.getTipoEntidad() != null)
                ? entidad.getTipoEntidad().getNombre() : null;

        // Los responsables van en consulta aparte (ver findConTodoPorId): fetch de dos
        // colecciones a la vez dispara MultipleBagFetchException en Hibernate.
        List<Responsable> responsables = (entidad == null) ? List.of()
                : responsableDao.findByEntidadIdWithPersona(entidad.getId());

        List<InscripcionDetalleDTO.ResponsableDetalle> rds = responsables.stream()
                .sorted(Comparator.comparing(r -> !r.isEsTitular())) // el titular primero
                .map(r -> {
                    var p = r.getPersona();
                    return new InscripcionDetalleDTO.ResponsableDetalle(
                            p != null ? p.getNombreCompleto() : null,
                            p != null ? p.getCi() : null,
                            p != null ? p.getCorreo() : null,
                            p != null ? p.getCelular() : null,
                            r.isEsTitular());
                })
                .collect(Collectors.toList());

        var items = i.getInscripcionPuestos();
        BigDecimal total = (items == null ? BigDecimal.ZERO :
                items.stream()
                        .map(InscripcionPuesto::getCosto)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<InscripcionDetalleDTO.PuestoDetalle> pds = (items == null ? List.<InscripcionDetalleDTO.PuestoDetalle>of() :
                items.stream()
                        .filter(ip -> ip.getPuesto() != null)
                        .sorted(Comparator.comparing(ip -> ip.getPuesto().getCodigo() == null ? "" : ip.getPuesto().getCodigo()))
                        .map(ip -> new InscripcionDetalleDTO.PuestoDetalle(
                                ip.getPuesto().getCodigo(),
                                ip.getPuesto().getCategoria() != null ? ip.getPuesto().getCategoria().getNombre() : null,
                                ip.getCosto() != null ? ip.getCosto() : BigDecimal.ZERO))
                        .collect(Collectors.toList()));

        List<InscripcionDetalleDTO.AuditoriaDetalle> auds = auditoriaDao
                .findByTablaAndIdRegistroOrderByFechaAsc("inscripcion", i.getId())
                .stream()
                .map(a -> new InscripcionDetalleDTO.AuditoriaDetalle(
                        a.getAccion(), a.getDetalle(), a.getUsuarioNombre(),
                        a.getOrigen(), a.getFecha()))
                .collect(Collectors.toList());

        return new InscripcionDetalleDTO(
                i.getId(),
                entidad != null ? entidad.getNombre() : null,
                entidad != null ? entidad.getNit() : null,
                tipo,
                entidad != null ? entidad.getRepresentanteLegal() : null,
                entidad != null ? entidad.getCiRepresentante() : null,
                entidad != null ? entidad.getDescripcion() : null,
                entidad != null ? entidad.getObjeto() : null,
                i.getFechaInicio() != null ? i.getFechaInicio().toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null,
                i.getFechaFin() != null ? i.getFechaFin().toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null,
                nombreDe(i.getRegistroUsuario()),
                rds,
                pds,
                total,
                i.getFechaCompra(),
                i.isPagoContado(),
                i.getEntidadBancaria(),
                i.getNumComprobante(),
                i.getImgComprobante(),
                i.getInscripcionEstado(),
                i.getEdicion() != null ? i.getEdicion().getNombre() : null,
                i.getMotivoCancelacion(),
                i.getFechaCancelacion(),
                nombreDe(i.getCanceladaPorUsuario()),
                i.getOrigenCancelacion(),
                auds);
    }

    private InscripcionListadoDTO aListado(Inscripcion i) {
        var entidad = i.getEntidad();
        var tipo = (entidad != null && entidad.getTipoEntidad() != null)
                ? entidad.getTipoEntidad().getNombre() : null;

        var items = i.getInscripcionPuestos();
        int cantidad = items != null ? items.size() : 0;

        BigDecimal total = (items == null ? BigDecimal.ZERO :
                items.stream()
                        .map(InscripcionPuesto::getCosto)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<String> categorias = (items == null ? List.<String>of() :
                items.stream()
                        .map(ip -> ip.getPuesto() != null && ip.getPuesto().getCategoria() != null
                                ? ip.getPuesto().getCategoria().getNombre() : null)
                        .filter(n -> n != null && !n.isBlank())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList()));

        List<String> codigosPuestos = (items == null ? List.<String>of() :
                items.stream()
                        .map(ip -> ip.getPuesto() != null ? ip.getPuesto().getCodigo() : null)
                        .filter(Objects::nonNull)
                        .sorted()
                        .collect(Collectors.toList()));

        return new InscripcionListadoDTO(
                i.getId(),
                nombreDe(i.getRegistroUsuario()),
                entidad != null ? entidad.getNombre() : null,
                tipo,
                entidad != null ? entidad.getNit() : null,
                i.getNumComprobante(),
                cantidad,
                categorias,
                total,
                i.getFechaCompra(),
                i.getInscripcionEstado(),
                i.getImgComprobante(),
                i.isPagoContado(),
                codigosPuestos,
                i.getMotivoCancelacion(),
                i.getFechaCancelacion(),
                nombreDe(i.getCanceladaPorUsuario()),
                i.getOrigenCancelacion());
    }

    /** Nombre para mostrar de un usuario: el de su persona, o el username como respaldo. */
    private String nombreDe(Usuario u) {
        return Optional.ofNullable(u)
                .map(us -> {
                    var p = us.getPersona();
                    return (p != null && p.getNombreCompleto() != null && !p.getNombreCompleto().isBlank())
                            ? p.getNombreCompleto()
                            : us.getUsername();
                })
                .orElse(null);
    }
}
