package com.usic.uniFex.model.service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.uniFex.model.IService.IAdministrativoService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Administrativo;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReciboPdfService {
    
    private final IInscripcionService inscripcionService;
    private final FuncionesInscripcion funcionesInscripcion;
    private final IResponsableService responsableService;
    private final IUsuarioService usuarioService;
    private final IAdministrativoService administrativoService;

    public void generarRecibo(Long idInscripcion, OutputStream os) throws Exception {
        Inscripcion ins = inscripcionService.findById(idInscripcion);
        if (ins == null) throw new IllegalArgumentException("Inscripción no encontrada");

        Entidad entidad = ins.getEntidad();

        // PUESTOS + TOTAL
        List<Map<String, Object>> puestos = funcionesInscripcion.obtener_puestos_por_inscripcion(idInscripcion);
        BigDecimal total = puestos.stream()
                .map(m -> m.get("costo") != null ? new BigDecimal(((Number)m.get("costo")).toString()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // RESPONSABLES
        List<Responsable> responsables = responsableService.findByEntidadId(entidad.getId());

        // === Documento en tamaño CARTA (LETTER) ===
        Document doc = new Document(PageSize.LETTER, 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, os);

        // === Membrete de fondo QUE CUBRE TODA LA HOJA, en cada página ===
        Image background = null;
        try {
            Path projectPath = Paths.get("").toAbsolutePath();
            String imagePath = projectPath + "/src/main/resources/static/assets/img/fondo/0.jpg";
            background = Image.getInstance(imagePath);
            // **NO** lo añadimos directo al doc; lo dibuja el PageEvent por debajo del contenido:
            writer.setPageEvent(new BackgroundEvent(background));
        } catch (Exception e) {
            System.err.println("No se pudo cargar el membrete: " + e.getMessage());
        }

        doc.open();

        doc.add(new Paragraph("\n\n"));

        // Fuentes
        Font fTitle = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        Font fBold  = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        Font fNorm  = new Font(Font.FontFamily.HELVETICA, 10);
        Font fSmall = new Font(Font.FontFamily.HELVETICA, 9);

        // Título
        Paragraph titulo = new Paragraph("FEXPO UAP V.1.0\nRECIBO / COMPROBANTE DE INSCRIPCIÓN", fTitle);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);

        doc.add(new Paragraph(" "));
        PdfPTable tCab = new PdfPTable(new float[]{28,72});
        tCab.setWidthPercentage(100);

        tCab.addCell(cell("Nro. Inscripción:", fBold));
        tCab.addCell(cell(String.valueOf(ins.getId()), fNorm));

        tCab.addCell(cell("Fecha emisión:", fBold));
        tCab.addCell(cell(formatFechaHora(new java.util.Date()), fNorm));

        tCab.addCell(cell("Vigencia:", fBold));
        tCab.addCell(cell(formatFecha(ins.getFechaInicio()) + " a " + formatFecha(ins.getFechaFin()), fNorm));

        tCab.addCell(cell("Estado:", fBold));
        tCab.addCell(cell(nvl(ins.getInscripcionEstado()), fNorm));
        doc.add(tCab);

        // Datos Entidad
        doc.add(spacer());
        doc.add(new Paragraph("Datos de la Entidad", fBold));
        PdfPTable tEnt = new PdfPTable(new float[]{28,72});
        tEnt.setWidthPercentage(100);
        tEnt.addCell(cell("Entidad:", fBold));             tEnt.addCell(cell(nvl(entidad.getNombre()), fNorm));
        tEnt.addCell(cell("Representante legal:", fBold));             tEnt.addCell(cell(nvl(entidad.getRepresentanteLegal() + " - " + entidad.getCiRepresentante()), fNorm));
        tEnt.addCell(cell("NIT:", fBold));                 tEnt.addCell(cell(nvl(entidad.getNit()), fNorm));
        tEnt.addCell(cell("Tipo:", fBold));                tEnt.addCell(cell(nvl(entidad.getTipoEntidad()!=null? entidad.getTipoEntidad().getNombre(): null), fNorm));
        tEnt.addCell(cell("Objetivo:", fBold));         tEnt.addCell(cell(nvl(entidad.getObjeto()), fNorm));
        doc.add(tEnt);

        // Responsables
        if (responsables != null && !responsables.isEmpty()) {
            doc.add(spacer());
            doc.add(new Paragraph("Responsables", fBold));
            PdfPTable tResp = new PdfPTable(new float[]{8,25,25,12,30});
            tResp.setWidthPercentage(100);
            header(tResp, "N°", "Nombres", "Apellidos", "CI", "Contacto");

            int i=1;
            for (Responsable r : responsables) {
                Persona p = r.getPersona();
                String ap = join(" ", nvl(p.getPaterno()), nvl(p.getMaterno())).trim();
                String contacto = join(" / ", nvl(p.getCorreo()), nvl(p.getCelular())).trim();
                tResp.addCell(cell(String.valueOf(i++), fNorm));
                tResp.addCell(cell(nvl(p.getNombre()), fNorm));
                tResp.addCell(cell(ap, fNorm));
                tResp.addCell(cell(nvl(p.getCi()), fNorm));
                tResp.addCell(cell(contacto, fNorm));
            }
            doc.add(tResp);
        }

        // Detalle de puestos
        doc.add(spacer());
        doc.add(new Paragraph("Detalle de Puestos Adquiridos", fBold));
        PdfPTable tDet = new PdfPTable(new float[]{15,25,25,35});
        tDet.setWidthPercentage(100);
        header(tDet, "Código", "Tamaño", "Categoria", "Costo (Bs)");

        for (Map<String,Object> m : puestos) {
            String cod   = nvl(m.get("codigo"));
            String tam   = nvl(m.get("tamano"));
            String cat   = nvl(m.get("categoria"));
            String costo = money(m.get("costo"));
            tDet.addCell(cell(cod, fNorm));
            tDet.addCell(cell(tam, fNorm));
            tDet.addCell(cell(cat, fNorm));
            tDet.addCell(cellRight(costo, fNorm));
        }

        PdfPCell cTotalLbl = cellRight("TOTAL (Bs):", fBold);
        cTotalLbl.setColspan(3);
        tDet.addCell(cTotalLbl);
        tDet.addCell(cellRight(money(total), fBold));
        doc.add(tDet);

        // Leyenda
        doc.add(spacer());
        Paragraph leyenda = new Paragraph(
            "Este documento constituye el recibo/comprobante de inscripción a la FEXPO UAP V.1.0. " +
            "La inscripción comprende los puestos detallados y su costo total. " +
            "La Unidad responsable se comunicará con usted para la formalización del contrato correspondiente. " +
            "Conserve este comprobante para futuras referencias.",
            fSmall
        );
        leyenda.setAlignment(Element.ALIGN_JUSTIFIED);
        doc.add(leyenda);

        // Sello de autenticación (lema + código + QR)
        doc.add(spacer());
        java.util.Date ahora = new java.util.Date();
        String fechaHoraEmision = formatFechaHora(ahora);
        String nitEntidad = nvl(entidad.getNit());
        String cadenaBase = ins.getId() + "|" + nitEntidad + "|" + fechaHoraEmision;

        String hash = sha256Hex(cadenaBase).substring(0, 16).toUpperCase();
        String codigoAut = "UAP-FEXPO-" + ins.getId() + "-" + hash;

        PdfPTable sello = new PdfPTable(new float[]{70, 30});
        sello.setWidthPercentage(100);

        Usuario usuario = usuarioService.findById(ins.getRegistroIdUsuario());
        Persona persona = usuario.getPersona();
        Administrativo administrativo = administrativoService.findByPersonaId(persona.getId()).orElse(null);

        String infoCodigo = "Código de autenticación: " + codigoAut + "\n" +
                            "Generado: " + fechaHoraEmision + "\n" +
                            "Usuario: " + administrativo.getCodigoFuncionario();
        PdfPCell cInfo = cell(infoCodigo, fSmall);
        cInfo.setBorder(Rectangle.NO_BORDER);
        cInfo.setPaddingTop(4f);
        sello.addCell(cInfo);

        BarcodeQRCode qr = new BarcodeQRCode(cadenaBase, 150, 150, null);
        Image qrImg = qr.getImage();
        qrImg.scaleAbsolute(80, 80);
        PdfPCell cQR = new PdfPCell(qrImg, false);
        cQR.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cQR.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cQR.setBorder(Rectangle.NO_BORDER);
        sello.addCell(cQR);

        doc.add(sello);

        doc.close();
    }

    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generando SHA-256", e);
        }
    }

    // ===== Helpers =====
    private PdfPCell cell(String txt, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(nvl(txt), f));
        c.setPadding(6f);
        return c;
    }
    private PdfPCell cellRight(String txt, Font f) {
        PdfPCell c = cell(txt, f);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }
    private PdfPCell cellCenter(String txt, Font f) {
        PdfPCell c = cell(txt, f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }
    private void header(PdfPTable t, String... cols) {
        Font fH = new Font(FontFamily.HELVETICA, 10, Font.BOLD);
        for (String s : cols) {
            PdfPCell h = cell(s, fH);
            h.setBackgroundColor(new BaseColor(240,240,240));
            t.addCell(h);
        }
    }
    private Paragraph spacer() { return new Paragraph(" "); }

    private static String nvl(Object o){ return o==null? "": String.valueOf(o); }

    private static String join(String sep, String... parts){
        return java.util.Arrays.stream(parts).filter(s -> s!=null && !s.trim().isEmpty())
                .reduce((a,b)->a+sep+b).orElse("");
    }

    private static String money(Object n){
        if(n==null) return "0.00";
        BigDecimal bd = new BigDecimal(n.toString());
        return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatFecha(Date d){
        if (d==null) return "";
        return new SimpleDateFormat("dd/MM/yyyy").format(d);
    }

    private static String formatFechaHora(java.util.Date d){
        if (d==null) return "";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(d);
    }

    static class BackgroundEvent extends PdfPageEventHelper {
        private final Image bg;

        BackgroundEvent(Image bg) {
            this.bg = bg;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (bg == null) return;
            try {
                Rectangle page = document.getPageSize();
                Image img = Image.getInstance(bg); // clonar instancia para no mutar la original
                img.scaleAbsolute(page.getWidth(), page.getHeight()); // cubrir total (ancho/alto exactos)
                img.setAbsolutePosition(0, 0);                        // esquina inferior izquierda
                PdfContentByte canvas = writer.getDirectContentUnder();
                canvas.addImage(img);
            } catch (Exception ex) {
                System.err.println("No se pudo dibujar el membrete: " + ex.getMessage());
            }
        }
    }
}
