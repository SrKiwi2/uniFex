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
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfContentByte;
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
 * <b>Formato duplicado:</b> tamaño carta (612×792 pt) con DOS mitades idénticas
 * (superior e inferior, ~396 pt cada una) separadas por una línea de corte punteada.
 * Cada mitad contiene todos los datos de la venta + QR verificable.
 *
 * Se imprime una hoja y se corta a la mitad: una copia para el cliente,
 * otra para archivo firmada/sellada.
 */
@Service
@RequiredArgsConstructor
public class ReciboPdfService {

    private final IInscripcionService inscripcionService;
    private final IResponsableDao responsableDao;
    private final IUsuarioService usuarioService;
    private final IAdministrativoService administrativoService;
    private final NotaVentaCodigoService codigoService;

    @Value("${unifex.nota.verificacion-url:}")
    private String urlVerificacion;

    private static final BaseColor GRIS_LINEA = new BaseColor(210, 214, 220);
    private static final BaseColor GRIS_FONDO = new BaseColor(243, 244, 246);
    private static final BaseColor GRIS_CORTE = new BaseColor(150, 156, 163);
    private static final BaseColor TINTA = new BaseColor(17, 24, 39);
    private static final BaseColor TINTA_SUAVE = new BaseColor(107, 114, 128);
    private static final BaseColor ROJO = new BaseColor(185, 28, 28);

    private static final DateTimeFormatter F_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Letter: 612 x 792 pt. Mitad ≈ 396 pt. Márgenes 36 pt → área útil ~360 pt por mitad.
    private static final float ALTO_PAGINA = PageSize.LETTER.getHeight(); // 792
    private static final float ANCHO_PAGINA = PageSize.LETTER.getWidth();  // 612
    private static final float MARGEN = 36f;
    private static final float ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN; // 540
    /**
     * Hueco reservado para la linea de corte, entre las dos mitades.
     *
     * Sin el, el contenido de la copia de arriba llegaba justo hasta la linea y las tijeras
     * pasaban por encima del pie. Aqui hay sitio para la linea punteada y su rotulo.
     */
    private static final float ALTO_CORTE = 18f;

    /** Lo que le toca a cada mitad, ya descontados los margenes y el hueco del corte. */
    private static final float ALTO_MITAD = (ALTO_PAGINA - 2 * MARGEN - ALTO_CORTE) / 2f;

    /**
     * Colchon que se le exige de sobra a cada mitad.
     *
     * {@code getTotalHeight()} mide las cajas de las celdas, pero las letras sobresalen un poco
     * de su caja y los bordes tienen grosor. Sin este margen, una mitad que "cabia" por medio
     * punto acababa mordiendo la linea de corte.
     */
    private static final float COLCHON = 6f;

    /**
     * Genera la nota de venta en formato DUPLICADO (dos mitades en una hoja carta).
     * Cada mitad es una nota completa con sus datos y QR verificable.
     */
    @Transactional(readOnly = true)
    public void generarRecibo(Long idInscripcion, OutputStream os) throws Exception {
        Inscripcion ins = inscripcionService.findById(idInscripcion);
        if (ins == null) throw new IllegalArgumentException("Inscripción no encontrada");

        Entidad entidad = ins.getEntidad();
        boolean anulada = "X".equalsIgnoreCase(ins.getEstado());

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

        String codigo = codigoService.obtenerOEmitir(idInscripcion);
        Inscripcion refrescada = inscripcionService.findById(idInscripcion);
        LocalDateTime emitida = refrescada != null && refrescada.getNotaEmitidaEn() != null
                ? refrescada.getNotaEmitidaEn()
                : LocalDateTime.now();

        Document doc = new Document(PageSize.LETTER, MARGEN, MARGEN, MARGEN, MARGEN);
        PdfWriter writer = PdfWriter.getInstance(doc, os);
        doc.open();
        PdfContentByte cb = writer.getDirectContent();

        Datos datos = new Datos(ins, entidad, anulada, detalle, total, responsables, codigo, emitida);

        // Se busca la escala mas grande con la que la mitad ENTERA cabe en su hueco.
        //
        // Antes se escribia de arriba abajo sin medir nada, y cuando la venta tenia varios
        // responsables o varias casetas el contenido se pasaba de largo: invadia la otra mitad y
        // el final —el total, el QR— se salia de la hoja. Con una hoja partida en dos para
        // recortar, no hay "pagina siguiente" a la que se pueda ir lo que sobra.
        //
        // Medir y encoger es lo unico que garantiza que quepa con CUALQUIER venta. Si ni al
        // minimo entra, las casetas pasan a listarse en una linea corrida en vez de una por fila.
        PdfPTable cuerpo = null;
        for (float esc : ESCALAS) {
            PdfPTable intento = construirMitad(datos, esc, false);
            if (intento.getTotalHeight() <= ALTO_MITAD - COLCHON) { cuerpo = intento; break; }
        }
        if (cuerpo == null) {
            for (float esc : ESCALAS) {
                PdfPTable intento = construirMitad(datos, esc, true);
                if (intento.getTotalHeight() <= ALTO_MITAD - COLCHON) { cuerpo = intento; break; }
            }
        }
        if (cuerpo == null) cuerpo = construirMitad(datos, ESCALAS[ESCALAS.length - 1], true);

        // Las dos mitades son identicas: se construye una vez y se escribe dos veces.
        for (int mitad = 0; mitad < 2; mitad++) {
            // La copia de abajo arranca en su tope y crece hacia el margen inferior; la de
            // arriba, desde el borde superior. En medio queda el hueco del corte.
            float topeSuperior = mitad == 0 ? MARGEN + ALTO_MITAD : ALTO_PAGINA - MARGEN;
            cuerpo.writeSelectedRows(0, -1, MARGEN, topeSuperior, cb);
        }

        dibujarLineaCorte(cb);
        doc.close();
    }

    /** Todo lo que necesita una mitad, junto, para no arrastrar doce parametros. */
    private record Datos(Inscripcion ins, Entidad entidad, boolean anulada,
                         List<InscripcionPuesto> detalle, BigDecimal total,
                         List<Responsable> responsables, String codigo, LocalDateTime emitida) {
    }

    /**
     * Una mitad completa como UNA tabla, para poder medirla antes de escribirla.
     *
     * @param esc      factor de escala de fuentes y separaciones (1 = tamaño base)
     * @param compacto las casetas en una linea corrida en vez de una fila cada una
     */
    private PdfPTable construirMitad(Datos d, float esc, boolean compacto) throws DocumentException {
        Font fMarca    = fuente(10.5f, esc, Font.BOLD, TINTA);
        Font fEdicion  = fuente(8f,    esc, Font.BOLD, TINTA_SUAVE);
        Font fSeccion  = fuente(6.2f,  esc, Font.BOLD, TINTA_SUAVE);
        Font fEtiqueta = fuente(6.4f,  esc, Font.BOLD, TINTA);
        Font fNorm     = fuente(7f,    esc, Font.NORMAL, TINTA);
        Font fSmall    = fuente(5.8f,  esc, Font.NORMAL, TINTA_SUAVE);
        Font fTotal    = fuente(9f,    esc, Font.BOLD, TINTA);
        Font fAnulada  = fuente(9f,    esc, Font.BOLD, ROJO);
        float pad = 2.6f * esc;
        float hueco = 4f * esc;

        PdfPTable hoja = new PdfPTable(1);
        hoja.setTotalWidth(ANCHO_UTIL);
        hoja.setLockedWidth(true);

        // ---- Cabecera: quien emite a la izquierda, identificacion a la derecha ----
        PdfPTable cab = tabla(new float[] { 62, 38 });
        Paragraph izq = new Paragraph();
        izq.add(new Phrase("UNIVERSIDAD AMAZÓNICA DE PANDO\n", fMarca));
        izq.add(new Phrase(nvl(d.ins().getEdicion() != null ? d.ins().getEdicion().getNombre() : "FEXPO UAP") + "\n", fEdicion));
        izq.add(new Phrase("NOTA DE VENTA · documento interno", fSmall));
        izq.setLeading(fMarca.getSize() * 1.15f);
        cab.addCell(sinBorde(izq));

        Paragraph der = new Paragraph();
        der.add(new Phrase("N.º " + d.ins().getId() + "\n", fEtiqueta));
        der.add(new Phrase("Código " + (d.codigo() != null ? d.codigo() : "—") + "\n", fEtiqueta));
        der.add(new Phrase(d.emitida().format(F_HORA), fSmall));
        der.setLeading(fEtiqueta.getSize() * 1.3f);
        der.setAlignment(Element.ALIGN_RIGHT);
        cab.addCell(sinBorde(der));
        hoja.addCell(envolver(cab, 0, hueco));

        hoja.addCell(separador(hueco));

        if (d.anulada()) {
            Paragraph av = new Paragraph("VENTA ANULADA"
                    + (vacio(d.ins().getMotivoCancelacion()) ? "" : " — " + d.ins().getMotivoCancelacion()), fAnulada);
            hoja.addCell(envolver(av, pad, hueco));
        }

        // ---- Entidad: rejilla de dos pares etiqueta/valor por fila, la mitad de alto ----
        hoja.addCell(rotulo("ENTIDAD", fSeccion, pad));
        PdfPTable ent = tabla(new float[] { 15, 35, 15, 35 });
        Entidad e = d.entidad();
        par(ent, "Entidad", e != null ? e.getNombre() : null, fEtiqueta, fNorm, pad);
        par(ent, "NIT", e != null ? e.getNit() : null, fEtiqueta, fNorm, pad);
        par(ent, "Rep. legal", unir(" · ", e != null ? e.getRepresentanteLegal() : null,
                e != null ? e.getCiRepresentante() : null), fEtiqueta, fNorm, pad);
        par(ent, "Tipo", e != null && e.getTipoEntidad() != null ? e.getTipoEntidad().getNombre() : null,
                fEtiqueta, fNorm, pad);
        par(ent, "Rubro", e != null ? e.getObjeto() : null, fEtiqueta, fNorm, pad);
        par(ent, "Vigencia", unir(" a ", formatFecha(d.ins().getFechaInicio()), formatFecha(d.ins().getFechaFin())),
                fEtiqueta, fNorm, pad);
        hoja.addCell(envolver(ent, 0, hueco));

        // ---- Responsables: nombre completo en una sola columna ----
        if (!d.responsables().isEmpty()) {
            hoja.addCell(rotulo("RESPONSABLES", fSeccion, pad));
            PdfPTable resp = tabla(new float[] { 52, 20, 28 });
            encabezado(resp, fSeccion, pad, -1, "Nombre", "C.I.", "Contacto");
            for (Responsable r : d.responsables()) {
                Persona p = r.getPersona();
                if (p == null) continue;
                celda(resp, unir(" ", p.getNombre(), p.getPaterno(), p.getMaterno())
                        + (r.isEsTitular() ? " (titular)" : ""), fNorm, pad);
                celda(resp, p.getCi(), fNorm, pad);
                celda(resp, unir(" · ", p.getCelular(), p.getCorreo()), fNorm, pad);
            }
            hoja.addCell(envolver(resp, 0, hueco));
        }

        // ---- Casetas ----
        hoja.addCell(rotulo("CASETAS", fSeccion, pad));
        if (compacto) {
            // Una linea corrida: cabe una venta de muchas casetas sin comerse la mitad entera.
            String lista = d.detalle().stream()
                    .map(ip -> nvl(ip.getPuesto().getCodigo())
                            + (ip.getPuesto().getCategoria() != null ? " " + ip.getPuesto().getCategoria().getNombre() : "")
                            + " (" + money(ip.getCosto()) + ")")
                    .reduce((a, b) -> a + " · " + b).orElse("—");
            PdfPTable comp = tabla(new float[] { 78, 22 });
            PdfPCell cl = new PdfPCell(new Phrase(lista, fNorm));
            cl.setBorder(Rectangle.BOTTOM);
            cl.setBorderColorBottom(GRIS_LINEA);
            cl.setPadding(pad);
            comp.addCell(cl);
            PdfPCell ct = new PdfPCell(new Phrase("TOTAL Bs " + money(d.total()), fTotal));
            ct.setHorizontalAlignment(Element.ALIGN_RIGHT);
            ct.setBackgroundColor(GRIS_FONDO);
            ct.setBorder(Rectangle.BOTTOM);
            ct.setBorderColorBottom(GRIS_LINEA);
            ct.setPadding(pad);
            comp.addCell(ct);
            hoja.addCell(envolver(comp, 0, hueco));
        } else {
            PdfPTable det = tabla(new float[] { 16, 16, 44, 24 });
            // El rotulo del importe va a la derecha, sobre sus cifras: alineado a la izquierda
            // quedaba a media tabla y parecia pertenecer a la columna de al lado.
            encabezado(det, fSeccion, pad, Element.ALIGN_RIGHT, "Código", "Tamaño", "Categoría", "Costo (Bs)");
            for (InscripcionPuesto ip : d.detalle()) {
                celda(det, ip.getPuesto().getCodigo(), fNorm, pad);
                celda(det, ip.getPuesto().getTamano(), fNorm, pad);
                celda(det, ip.getPuesto().getCategoria() != null ? ip.getPuesto().getCategoria().getNombre() : null, fNorm, pad);
                celdaDerecha(det, money(ip.getCosto()), fNorm, pad);
            }
            PdfPCell lbl = celdaSuelta("TOTAL Bs", fTotal, pad);
            lbl.setColspan(3);
            lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
            lbl.setBackgroundColor(GRIS_FONDO);
            det.addCell(lbl);
            PdfPCell val = celdaSuelta(money(d.total()), fTotal, pad);
            val.setHorizontalAlignment(Element.ALIGN_RIGHT);
            val.setBackgroundColor(GRIS_FONDO);
            det.addCell(val);
            hoja.addCell(envolver(det, 0, hueco));
        }

        // ---- Pago y vendedor, en una sola fila de cuatro columnas ----
        hoja.addCell(rotulo("PAGO", fSeccion, pad));
        PdfPTable pago = tabla(new float[] { 15, 35, 15, 35 });
        par(pago, "Forma", d.ins().isPagoContado() ? "Contado" : "Depósito / transferencia", fEtiqueta, fNorm, pad);
        par(pago, "Vendedor", quienEmite(d.ins()), fEtiqueta, fNorm, pad);
        if (!d.ins().isPagoContado()) {
            par(pago, "Banco", d.ins().getEntidadBancaria(), fEtiqueta, fNorm, pad);
            par(pago, "N.º comprob.", d.ins().getNumComprobante() != null
                    ? String.valueOf(d.ins().getNumComprobante()) : null, fEtiqueta, fNorm, pad);
        }
        hoja.addCell(envolver(pago, 0, hueco));

        // ---- Pie: verificacion + QR, y el espacio de firma ----
        hoja.addCell(separador(hueco * 0.5f));
        PdfPTable pie = tabla(new float[] { 58, 20, 22 });

        Paragraph verif = new Paragraph();
        verif.add(new Phrase("Verificación · ", fEtiqueta));
        verif.add(new Phrase("Nota de venta interna, no es factura. Su validez se comprueba con el "
                + "código impreso: escanee el QR o consúltelo en el sistema. Una copia sin código, "
                + "o con uno que el sistema no reconozca, no respalda ninguna venta.", fSmall));
        verif.setLeading(fSmall.getSize() * 1.25f);
        pie.addCell(sinBorde(verif));

        Paragraph firma = new Paragraph();
        firma.add(new Phrase("\n\n_______________________\n", fSmall));
        firma.add(new Phrase("Firma y sello", fSmall));
        firma.setAlignment(Element.ALIGN_CENTER);
        firma.setLeading(fSmall.getSize() * 1.2f);
        pie.addCell(sinBorde(firma));

        if (d.codigo() != null) {
            String contenidoQr = vacio(urlVerificacion) ? d.codigo() : urlVerificacion + d.codigo();
            BarcodeQRCode qr = new BarcodeQRCode(contenidoQr, 200, 200, null);
            Image img = qr.getImage();
            float lado = 54f * esc;
            img.scaleAbsolute(lado, lado);
            PdfPCell cq = new PdfPCell(img, false);
            cq.setBorder(Rectangle.NO_BORDER);
            cq.setHorizontalAlignment(Element.ALIGN_RIGHT);
            pie.addCell(cq);
        } else {
            pie.addCell(sinBorde(new Paragraph("")));
        }
        hoja.addCell(envolver(pie, 0, 0));

        return hoja;
    }

    /** Escalas que se prueban, de la mas legible a la mas apretada. */
    private static final float[] ESCALAS = { 1f, 0.94f, 0.88f, 0.82f, 0.76f, 0.7f, 0.64f };

    private static Font fuente(float base, float esc, int estilo, BaseColor color) {
        return new Font(FontFamily.HELVETICA, base * esc, estilo, color);
    }

    private static PdfPTable tabla(float[] anchos) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        return t;
    }

    /** Mete un elemento en una celda sin bordes de la tabla exterior, con su separacion abajo. */
    private static PdfPCell envolver(Element contenido, float pad, float huecoAbajo) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(pad);
        c.setPaddingBottom(pad + huecoAbajo);
        c.addElement(contenido);
        return c;
    }

    private static PdfPCell separador(float hueco) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(GRIS_LINEA);
        c.setFixedHeight(Math.max(1f, hueco));
        return c;
    }

    private static PdfPCell rotulo(String txt, Font f, float pad) {
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(pad);
        c.setPaddingBottom(pad * 0.5f);
        return c;
    }

    /** Una pareja etiqueta/valor dentro de una rejilla de cuatro columnas. */
    private static void par(PdfPTable t, String etiqueta, String valor, Font fEt, Font fVal, float pad) {
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, fEt));
        e.setBorder(Rectangle.BOTTOM);
        e.setBorderColorBottom(GRIS_LINEA);
        e.setPadding(pad);
        t.addCell(e);
        PdfPCell v = new PdfPCell(new Phrase(vacio(valor) ? "—" : valor, fVal));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColorBottom(GRIS_LINEA);
        v.setPadding(pad);
        t.addCell(v);
    }

    /** Línea de corte punteada centrada entre las dos mitades. */
    private void dibujarLineaCorte(PdfContentByte cb) {
        float yCorte = MARGEN + ALTO_MITAD + ALTO_CORTE / 2f; // centrado en el hueco
        cb.saveState();
        cb.setLineWidth(0.8f);
        cb.setLineDash(4f, 4f); // 4pt trazo, 4pt espacio
        cb.setColorStroke(GRIS_CORTE);
        cb.moveTo(MARGEN + 20, yCorte);
        cb.lineTo(ANCHO_PAGINA - MARGEN - 20, yCorte);
        cb.stroke();
        // Texto "CORTAR AQUÍ" - usar font base directamente
        try {
            com.itextpdf.text.pdf.BaseFont bf = com.itextpdf.text.pdf.BaseFont.createFont(
                    com.itextpdf.text.pdf.BaseFont.HELVETICA,
                    com.itextpdf.text.pdf.BaseFont.CP1252,
                    com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED);
            cb.beginText();
            cb.setFontAndSize(bf, 7);
            cb.setColorFill(GRIS_CORTE);
            cb.showTextAligned(Element.ALIGN_CENTER, "CORTAR AQUI", ANCHO_PAGINA / 2f, yCorte - 10, 0);
            cb.endText();
        } catch (com.itextpdf.text.DocumentException | java.io.IOException ignored) {}
        cb.restoreState();
    }

    // ===== Helpers de celdas =====

    /**
     * Fila de encabezado. {@code alineaUltima} alinea la ULTIMA columna (o -1 para dejarla como
     * las demas): el rotulo del importe tiene que caer sobre sus cifras, no a media tabla.
     */
    private static void encabezado(PdfPTable t, Font f, float pad, int alineaUltima, String... cols) {
        for (int i = 0; i < cols.length; i++) {
            PdfPCell h = new PdfPCell(new Phrase(cols[i], f));
            h.setBackgroundColor(GRIS_FONDO);
            h.setBorder(Rectangle.BOTTOM);
            h.setBorderColorBottom(GRIS_LINEA);
            h.setPadding(pad);
            if (alineaUltima >= 0 && i == cols.length - 1) h.setHorizontalAlignment(alineaUltima);
            t.addCell(h);
        }
    }

    private static void celda(PdfPTable t, String txt, Font f, float pad) {
        t.addCell(celdaSuelta(vacio(txt) ? "—" : txt, f, pad));
    }

    private static void celdaDerecha(PdfPTable t, String txt, Font f, float pad) {
        PdfPCell c = celdaSuelta(txt, f, pad);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private static PdfPCell celdaSuelta(String txt, Font f, float pad) {
        PdfPCell c = new PdfPCell(new Phrase(nvl(txt), f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(GRIS_LINEA);
        c.setPadding(pad);
        return c;
    }

    private static PdfPCell sinBorde(Paragraph p) {
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    // ===== Helpers de datos =====

    private static boolean vacio(String s) { return s == null || s.trim().isEmpty(); }
    private static String nvl(Object o) { return o == null ? "" : String.valueOf(o); }

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

    /** Quien vendió. Degrada con cuidado: nombre completo → código funcionario → username. */
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
}