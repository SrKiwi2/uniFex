package com.usic.uniFex.model.service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.uniFex.model.IService.IAdministrativoService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Administrativo;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * La nota de venta de una inscripcion, en PDF.
 *
 * Es un documento INTERNO (no una factura fiscal): respalda que la venta existe y que se
 * cobro, y por eso lo que mas importa aqui no es el adorno sino que (a) los datos sean los de
 * ESA venta y (b) el papel se pueda comprobar contra el sistema.
 *
 * <h2>Lo que estaba mal y se corrigio</h2>
 * <ul>
 *   <li><b>Responsables de otra venta.</b> Se listaban TODOS los de la entidad, incluidos los
 *       dados de baja. Ahora salen los vigentes y el titular primero.</li>
 *   <li><b>"null - null".</b> El representante legal concatenaba antes de comprobar el nulo,
 *       asi que una entidad sin representante imprimia literalmente esa palabra.</li>
 *   <li><b>Fecha que cambiaba en cada impresion.</b> Se imprimia {@code new Date()}, de modo
 *       que la misma venta salia con fecha distinta cada vez — y el codigo tambien.</li>
 *   <li><b>Una venta cancelada se imprimia como si nada.</b> Ahora sale marcada ANULADA.</li>
 *   <li><b>Faltaba el pago</b> (contado o banco y comprobante), que es justo lo que respalda
 *       el documento.</li>
 *   <li><b>El membrete no cargaba en produccion:</b> se leia de {@code src/main/resources/...}
 *       relativo al directorio de ejecucion, que no existe al correr desde el jar. Fallaba en
 *       silencio. Se quito: el documento ahora es sobrio a proposito, sin imagen de fondo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReciboPdfService {

    private final IInscripcionService inscripcionService;
    private final IResponsableDao responsableDao;
    private final IUsuarioService usuarioService;
    private final IAdministrativoService administrativoService;
    private final NotaVentaCodigoService codigoService;

    /**
     * Donde apunta el QR. Si esta vacio, el QR lleva solo el codigo: se puede teclear a mano
     * en el verificador. Con la URL puesta, escanear la nota abre la comprobacion directa.
     */
    @Value("${unifex.nota.verificacion-url:}")
    private String urlVerificacion;

    private static final BaseColor GRIS_LINEA = new BaseColor(210, 214, 220);
    private static final BaseColor GRIS_FONDO = new BaseColor(243, 244, 246);
    private static final BaseColor TINTA = new BaseColor(17, 24, 39);
    private static final BaseColor TINTA_SUAVE = new BaseColor(107, 114, 128);
    private static final BaseColor ROJO = new BaseColor(185, 28, 28);

    private static final DateTimeFormatter F_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Genera la nota sobre el stream dado.
     *
     * `@Transactional(readOnly = true)` no es decorativo: el metodo recorre relaciones LAZY
     * (entidad, tipo de entidad, edicion, persona del usuario). Fuera de una peticion web
     * —una prueba, una tarea programada, un envio por correo— sin esto revienta con
     * LazyInitializationException. La emision del codigo va en su propia transaccion de
     * escritura (ver {@link NotaVentaCodigoService}), justamente porque esta es de lectura.
     */
    @Transactional(readOnly = true)
    public void generarRecibo(Long idInscripcion, OutputStream os) throws Exception {
        Inscripcion ins = inscripcionService.findById(idInscripcion);
        if (ins == null) throw new IllegalArgumentException("Inscripción no encontrada");

        Entidad entidad = ins.getEntidad();
        boolean anulada = "X".equalsIgnoreCase(ins.getEstado());

        // El detalle sale del propio grafo JPA y no de `obtener_puestos_por_inscripcion`.
        // Esa funcion devuelve SOLO codigo, tamano y costo: no tiene columna de categoria, asi
        // que la columna "Categoria" del recibo salia vacia desde siempre. Aqui estan los tres
        // datos y ademas la categoria, y el costo sigue siendo el CONGELADO en la venta
        // (`inscripcion_puesto.costo`), no el precio actual de la categoria.
        List<InscripcionPuesto> detalle = ins.getInscripcionPuestos() == null
                ? List.of()
                : ins.getInscripcionPuestos().stream()
                        .filter(ip -> ip.getPuesto() != null)
                        .sorted(java.util.Comparator.comparing(
                                ip -> nvl(ip.getPuesto().getCodigo()),
                                java.util.Comparator.naturalOrder()))
                        .toList();
        BigDecimal total = detalle.stream()
                .map(ip -> ip.getCosto() != null ? ip.getCosto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Responsable> responsables = entidad != null
                ? responsableDao.findVigentesDeEntidad(entidad.getId())
                : List.of();

        // El codigo se emite la primera vez y despues no cambia: es lo que hace que dos
        // copias de la misma nota sean el mismo documento.
        String codigo = codigoService.obtenerOEmitir(idInscripcion);
        Inscripcion refrescada = inscripcionService.findById(idInscripcion);
        LocalDateTime emitida = refrescada != null && refrescada.getNotaEmitidaEn() != null
                ? refrescada.getNotaEmitidaEn()
                : LocalDateTime.now();

        Document doc = new Document(PageSize.LETTER, 42, 42, 40, 46);
        PdfWriter.getInstance(doc, os);
        doc.open();

        Font fMarca = new Font(FontFamily.HELVETICA, 15, Font.BOLD, TINTA);
        Font fDoc = new Font(FontFamily.HELVETICA, 11, Font.BOLD, TINTA_SUAVE);
        Font fSeccion = new Font(FontFamily.HELVETICA, 9, Font.BOLD, TINTA_SUAVE);
        Font fEtiqueta = new Font(FontFamily.HELVETICA, 9, Font.BOLD, TINTA);
        Font fNorm = new Font(FontFamily.HELVETICA, 9.5f, Font.NORMAL, TINTA);
        Font fSmall = new Font(FontFamily.HELVETICA, 8, Font.NORMAL, TINTA_SUAVE);
        Font fTotal = new Font(FontFamily.HELVETICA, 12, Font.BOLD, TINTA);
        Font fAnulada = new Font(FontFamily.HELVETICA, 13, Font.BOLD, ROJO);

        // ---- Cabecera: quien emite a la izquierda, identificacion del documento a la derecha
        String edicionNombre = ins.getEdicion() != null ? ins.getEdicion().getNombre() : "FEXPO UAP";
        PdfPTable cab = new PdfPTable(new float[] { 60, 40 });
        cab.setWidthPercentage(100);

        Paragraph marca = new Paragraph();
        marca.add(new Phrase("Universidad Adventista de Bolivia\n", fMarca));
        marca.add(new Phrase(edicionNombre + "\n", fDoc));
        marca.add(new Phrase("NOTA DE VENTA · documento interno", fSmall));
        cab.addCell(sinBorde(marca));

        PdfPTable ident = new PdfPTable(new float[] { 45, 55 });
        ident.setWidthPercentage(100);
        identFila(ident, "N.º", String.valueOf(ins.getId()), fSmall, fEtiqueta);
        identFila(ident, "Emitida", emitida.format(F_HORA), fSmall, fNorm);
        identFila(ident, "Código", codigo != null ? codigo : "—", fSmall, fEtiqueta);
        PdfPCell cIdent = new PdfPCell(ident);
        cIdent.setBorder(Rectangle.NO_BORDER);
        cab.addCell(cIdent);
        doc.add(cab);

        doc.add(linea());

        if (anulada) {
            Paragraph aviso = new Paragraph(
                    "VENTA ANULADA" + (vacio(ins.getMotivoCancelacion()) ? "" : " — " + ins.getMotivoCancelacion()),
                    fAnulada);
            aviso.setSpacingBefore(6f);
            aviso.setSpacingAfter(2f);
            doc.add(aviso);
            doc.add(new Paragraph("Esta nota ya no ampara la compra de las casetas detalladas.", fSmall));
        }

        // ---- Entidad
        doc.add(seccion("DATOS DE LA ENTIDAD", fSeccion));
        PdfPTable tEnt = new PdfPTable(new float[] { 26, 74 });
        tEnt.setWidthPercentage(100);
        dato(tEnt, "Entidad", entidad != null ? entidad.getNombre() : null, fEtiqueta, fNorm);
        dato(tEnt, "NIT", entidad != null ? entidad.getNit() : null, fEtiqueta, fNorm);
        // El " - " solo aparece si hay las dos partes: antes se concatenaba primero y una
        // entidad sin representante imprimia "null - null".
        dato(tEnt, "Representante legal",
                unir(" · ", entidad != null ? entidad.getRepresentanteLegal() : null,
                        entidad != null ? entidad.getCiRepresentante() : null),
                fEtiqueta, fNorm);
        dato(tEnt, "Tipo",
                entidad != null && entidad.getTipoEntidad() != null ? entidad.getTipoEntidad().getNombre() : null,
                fEtiqueta, fNorm);
        dato(tEnt, "Rubro", entidad != null ? entidad.getObjeto() : null, fEtiqueta, fNorm);
        dato(tEnt, "Vigencia",
                unir(" a ", formatFecha(ins.getFechaInicio()), formatFecha(ins.getFechaFin())),
                fEtiqueta, fNorm);
        doc.add(tEnt);

        // ---- Responsables
        if (!responsables.isEmpty()) {
            doc.add(seccion("RESPONSABLES", fSeccion));
            PdfPTable tResp = new PdfPTable(new float[] { 24, 30, 16, 30 });
            tResp.setWidthPercentage(100);
            encabezado(tResp, fSeccion, "Nombre", "Apellidos", "C.I.", "Contacto");
            for (Responsable r : responsables) {
                Persona p = r.getPersona();
                if (p == null) continue;
                String rol = r.isEsTitular() ? " (titular)" : "";
                celda(tResp, nvl(p.getNombre()) + rol, fNorm);
                celda(tResp, unir(" ", p.getPaterno(), p.getMaterno()), fNorm);
                celda(tResp, nvl(p.getCi()), fNorm);
                celda(tResp, unir(" · ", p.getCelular(), p.getCorreo()), fNorm);
            }
            doc.add(tResp);
        }

        // ---- Detalle
        doc.add(seccion("CASETAS", fSeccion));
        PdfPTable tDet = new PdfPTable(new float[] { 18, 18, 42, 22 });
        tDet.setWidthPercentage(100);
        encabezado(tDet, fSeccion, "Código", "Tamaño", "Categoría", "Costo (Bs)");
        for (InscripcionPuesto ip : detalle) {
            celda(tDet, ip.getPuesto().getCodigo(), fNorm);
            celda(tDet, ip.getPuesto().getTamano(), fNorm);
            celda(tDet, ip.getPuesto().getCategoria() != null
                    ? ip.getPuesto().getCategoria().getNombre() : null, fNorm);
            celdaDerecha(tDet, money(ip.getCosto()), fNorm);
        }
        PdfPCell lblTotal = celdaSuelta("TOTAL Bs", fTotal);
        lblTotal.setColspan(3);
        lblTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lblTotal.setBackgroundColor(GRIS_FONDO);
        tDet.addCell(lblTotal);
        PdfPCell valTotal = celdaSuelta(money(total), fTotal);
        valTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valTotal.setBackgroundColor(GRIS_FONDO);
        tDet.addCell(valTotal);
        doc.add(tDet);

        // ---- Pago: es lo que respalda el documento y no se imprimia
        doc.add(seccion("PAGO", fSeccion));
        PdfPTable tPago = new PdfPTable(new float[] { 26, 74 });
        tPago.setWidthPercentage(100);
        dato(tPago, "Forma", ins.isPagoContado() ? "Contado" : "Depósito / transferencia", fEtiqueta, fNorm);
        if (!ins.isPagoContado()) {
            dato(tPago, "Banco", ins.getEntidadBancaria(), fEtiqueta, fNorm);
            dato(tPago, "N.º comprobante",
                    ins.getNumComprobante() != null ? String.valueOf(ins.getNumComprobante()) : null,
                    fEtiqueta, fNorm);
        }
        dato(tPago, "Vendedor", quienEmite(ins), fEtiqueta, fNorm);
        doc.add(tPago);

        // ---- Sello verificable
        doc.add(linea());
        PdfPTable sello = new PdfPTable(new float[] { 72, 28 });
        sello.setWidthPercentage(100);

        Paragraph texto = new Paragraph();
        texto.add(new Phrase("Verificación\n", fEtiqueta));
        texto.add(new Phrase(
                "Este documento es una nota de venta interna, no una factura. Su validez se comprueba "
                        + "con el código impreso: escanee el código QR o consúltelo en el sistema. "
                        + "Una copia sin código, o con un código que el sistema no reconozca, no respalda ninguna venta.\n\n",
                fSmall));
        texto.add(new Phrase("Código: " + (codigo != null ? codigo : "—") + "\n", fEtiqueta));
        texto.add(new Phrase("Emitida: " + emitida.format(F_HORA), fSmall));
        sello.addCell(sinBorde(texto));

        if (codigo != null) {
            String contenidoQr = vacio(urlVerificacion) ? codigo : urlVerificacion + codigo;
            BarcodeQRCode qr = new BarcodeQRCode(contenidoQr, 200, 200, null);
            Image qrImg = qr.getImage();
            qrImg.scaleAbsolute(78, 78);
            PdfPCell cQr = new PdfPCell(qrImg, false);
            cQr.setBorder(Rectangle.NO_BORDER);
            cQr.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sello.addCell(cQr);
        } else {
            sello.addCell(sinBorde(new Paragraph("")));
        }
        doc.add(sello);

        doc.close();
    }

    /** Quien vendio. Se degrada con cuidado: nombre completo, si no el codigo, si no el usuario. */
    private String quienEmite(Inscripcion ins) {
        Usuario usuario = ins.getRegistroIdUsuario() != null
                ? usuarioService.findById(ins.getRegistroIdUsuario())
                : null;
        Persona persona = usuario != null ? usuario.getPersona() : null;
        if (persona != null) {
            String nombre = unir(" ", persona.getNombre(), persona.getPaterno(), persona.getMaterno());
            if (!vacio(nombre)) return nombre;
        }
        Administrativo adm = persona != null
                ? administrativoService.findByPersonaId(persona.getId()).orElse(null)
                : null;
        if (adm != null && adm.getCodigoFuncionario() != null) return adm.getCodigoFuncionario();
        return usuario != null && usuario.getUsername() != null ? usuario.getUsername() : "—";
    }

    // ===== Helpers de maquetacion =====

    private Paragraph seccion(String txt, Font f) {
        Paragraph p = new Paragraph(txt, f);
        p.setSpacingBefore(14f);
        p.setSpacingAfter(5f);
        return p;
    }

    private PdfPTable linea() {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(GRIS_LINEA);
        c.setFixedHeight(8f);
        t.addCell(c);
        return t;
    }

    private PdfPCell sinBorde(Paragraph p) {
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private void identFila(PdfPTable t, String etiqueta, String valor, Font fEt, Font fVal) {
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, fEt));
        e.setBorder(Rectangle.NO_BORDER);
        e.setPaddingBottom(2f);
        t.addCell(e);
        PdfPCell v = new PdfPCell(new Phrase(nvl(valor), fVal));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPaddingBottom(2f);
        t.addCell(v);
    }

    /** Fila etiqueta/valor. Un valor vacio se imprime como "—" y no como un hueco. */
    private void dato(PdfPTable t, String etiqueta, String valor, Font fEt, Font fVal) {
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, fEt));
        e.setBorder(Rectangle.BOTTOM);
        e.setBorderColorBottom(GRIS_LINEA);
        e.setPadding(5f);
        t.addCell(e);
        PdfPCell v = new PdfPCell(new Phrase(vacio(valor) ? "—" : valor, fVal));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColorBottom(GRIS_LINEA);
        v.setPadding(5f);
        t.addCell(v);
    }

    private void encabezado(PdfPTable t, Font f, String... cols) {
        for (String s : cols) {
            PdfPCell h = new PdfPCell(new Phrase(s, f));
            h.setBackgroundColor(GRIS_FONDO);
            h.setBorder(Rectangle.BOTTOM);
            h.setBorderColorBottom(GRIS_LINEA);
            h.setPadding(5f);
            t.addCell(h);
        }
    }

    private void celda(PdfPTable t, String txt, Font f) {
        t.addCell(celdaSuelta(vacio(txt) ? "—" : txt, f));
    }

    private void celdaDerecha(PdfPTable t, String txt, Font f) {
        PdfPCell c = celdaSuelta(txt, f);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private PdfPCell celdaSuelta(String txt, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(nvl(txt), f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(GRIS_LINEA);
        c.setPadding(5f);
        return c;
    }

    // ===== Helpers de datos =====

    private static boolean vacio(String s) { return s == null || s.trim().isEmpty(); }

    private static String nvl(Object o) { return o == null ? "" : String.valueOf(o); }

    /** Une solo las partes que existen: evita los "null - null" y los separadores sueltos. */
    private static String unir(String sep, String... partes) {
        return java.util.Arrays.stream(partes)
                .filter(s -> s != null && !s.trim().isEmpty())
                .reduce((a, b) -> a + sep + b)
                .orElse("");
    }

    private static String money(Object n) {
        if (n == null) return "0.00";
        return new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatFecha(Date d) {
        return d == null ? "" : new SimpleDateFormat("dd/MM/yyyy").format(d);
    }
}
