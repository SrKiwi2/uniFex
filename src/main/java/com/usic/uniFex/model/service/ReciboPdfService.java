package com.usic.uniFex.model.service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.*;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IEntidadService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReciboPdfService {
    
    private final IInscripcionService inscripcionService;
    private final FuncionesInscripcion funcionesInscripcion;
    private final IResponsableService responsableService; // si necesitas recuperar responsables
    private final IEntidadService entidadService;

    // Utiliza iText 5 / OpenPDF (mismo código base)
    public void generarRecibo(Long idInscripcion, OutputStream os) throws Exception {
        Inscripcion ins = inscripcionService.findById(idInscripcion);
        if (ins == null) throw new IllegalArgumentException("Inscripción no encontrada");

        Entidad entidad = ins.getEntidad();

        // PUESTOS
        List<Map<String, Object>> puestos = funcionesInscripcion.obtener_puestos_por_inscripcion(idInscripcion);
        BigDecimal total = puestos.stream()
                .map(m -> m.get("costo") != null ? new BigDecimal(((Number)m.get("costo")).toString()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Responsables (si tu modelo guarda Responsable por entidad)
        List<Responsable> responsables = responsableService.findByEntidadId(entidad.getId()); 
        // o si no, arma los responsables desde otra consulta

        // ====== PDF ======
        com.itextpdf.text.Document doc = new com.itextpdf.text.Document(com.itextpdf.text.PageSize.A4, 36, 36, 36, 36);
        com.itextpdf.text.pdf.PdfWriter.getInstance(doc, os);
        doc.open();

        // Fuentes
        com.itextpdf.text.Font fTitle = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 14, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font fBold  = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 10, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font fNorm  = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 10);
        com.itextpdf.text.Font fSmall = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 9);

        // Encabezado
        com.itextpdf.text.Paragraph titulo = new com.itextpdf.text.Paragraph("FEXPO UAP V.1.0\nRECIBO / COMPROBANTE DE INSCRIPCIÓN", fTitle);
        titulo.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
        doc.add(titulo);

        doc.add(new com.itextpdf.text.Paragraph(" "));
        com.itextpdf.text.pdf.PdfPTable tCab = new com.itextpdf.text.pdf.PdfPTable(new float[]{28,72});
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
        doc.add(new com.itextpdf.text.Paragraph("Datos de la Entidad", fBold));
        com.itextpdf.text.pdf.PdfPTable tEnt = new com.itextpdf.text.pdf.PdfPTable(new float[]{28,72});
        tEnt.setWidthPercentage(100);
        tEnt.addCell(cell("Entidad:", fBold));             tEnt.addCell(cell(nvl(entidad.getNombre()), fNorm));
        tEnt.addCell(cell("NIT:", fBold));                 tEnt.addCell(cell(nvl(entidad.getNit()), fNorm));
        tEnt.addCell(cell("Tipo:", fBold));                tEnt.addCell(cell(nvl(entidad.getTipoEntidad()!=null? entidad.getTipoEntidad().getNombre(): null), fNorm));
        tEnt.addCell(cell("Descripción:", fBold));         tEnt.addCell(cell(nvl(entidad.getDescripcion()), fNorm));
        doc.add(tEnt);

        // Responsables
        if (responsables != null && !responsables.isEmpty()) {
            doc.add(spacer());
            doc.add(new com.itextpdf.text.Paragraph("Responsables", fBold));
            com.itextpdf.text.pdf.PdfPTable tResp = new com.itextpdf.text.pdf.PdfPTable(new float[]{8,25,25,12,30});
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
        doc.add(new com.itextpdf.text.Paragraph("Detalle de Puestos Adquiridos", fBold));
        com.itextpdf.text.pdf.PdfPTable tDet = new com.itextpdf.text.pdf.PdfPTable(new float[]{15,25,25,35});
        tDet.setWidthPercentage(100);
        header(tDet, "Código", "Tamaño", "Estado", "Costo (Bs)");

        for (Map<String,Object> m : puestos) {
            String cod   = nvl(m.get("codigo"));
            String tam   = nvl(m.get("tamano"));
            String est   = nvl(m.get("estado_puesto")); // O/L según tu consulta
            String costo = money(m.get("costo"));
            tDet.addCell(cell(cod, fNorm));
            tDet.addCell(cell(tam, fNorm));
            tDet.addCell(cell(est, fNorm));
            tDet.addCell(cellRight(costo, fNorm));
        }
        // Total
        com.itextpdf.text.pdf.PdfPCell cTotalLbl = cellRight("TOTAL (Bs):", fBold);
        cTotalLbl.setColspan(3);
        tDet.addCell(cTotalLbl);
        tDet.addCell(cellRight(money(total), fBold));
        doc.add(tDet);

        // Observaciones / leyenda
        doc.add(spacer());
        com.itextpdf.text.Paragraph leyenda = new com.itextpdf.text.Paragraph(
            "Este documento constituye el recibo/comprobante de inscripción a la FEXPO UAP V.1.0. " +
            "La inscripción comprende los puestos detallados y su costo total. " +
            "La Unidad responsable se comunicará con usted para la formalización del contrato correspondiente. " +
            "Conserve este comprobante para futuras referencias.",
            fSmall
        );
        leyenda.setAlignment(com.itextpdf.text.Element.ALIGN_JUSTIFIED);
        doc.add(leyenda);

        // Si deseas, agrega líneas de firma
        doc.add(spacer());
        com.itextpdf.text.pdf.PdfPTable firmas = new com.itextpdf.text.pdf.PdfPTable(2);
        firmas.setWidthPercentage(100);
        com.itextpdf.text.pdf.PdfPCell fc1 = cellCenter("_____________________________\nResponsable de la Entidad", fSmall);
        com.itextpdf.text.pdf.PdfPCell fc2 = cellCenter("_____________________________\nFEXPO UAP", fSmall);
        fc1.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        fc2.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        firmas.addCell(fc1); firmas.addCell(fc2);
        doc.add(firmas);

        doc.close();
    }

    // ===== Helpers =====
    private com.itextpdf.text.pdf.PdfPCell cell(String txt, com.itextpdf.text.Font f) {
        com.itextpdf.text.pdf.PdfPCell c = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(nvl(txt), f));
        c.setPadding(6f);
        return c;
    }
    private com.itextpdf.text.pdf.PdfPCell cellRight(String txt, com.itextpdf.text.Font f) {
        com.itextpdf.text.pdf.PdfPCell c = cell(txt, f);
        c.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_RIGHT);
        return c;
    }
    private com.itextpdf.text.pdf.PdfPCell cellCenter(String txt, com.itextpdf.text.Font f) {
        com.itextpdf.text.pdf.PdfPCell c = cell(txt, f);
        c.setHorizontalAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
        return c;
    }
    private void header(com.itextpdf.text.pdf.PdfPTable t, String... cols) {
        com.itextpdf.text.Font fH = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 10, com.itextpdf.text.Font.BOLD);
        for (String s : cols) {
            com.itextpdf.text.pdf.PdfPCell h = cell(s, fH);
            h.setBackgroundColor(new com.itextpdf.text.BaseColor(240,240,240));
            t.addCell(h);
        }
    }
    private com.itextpdf.text.Paragraph spacer() { return new com.itextpdf.text.Paragraph(" "); }
    private static String nvl(Object o){ return o==null? "": String.valueOf(o); }
    private static String join(String sep, String... parts){
        return java.util.Arrays.stream(parts).filter(s -> s!=null && !s.trim().isEmpty())
                .reduce((a,b)->a+sep+b).orElse("");
    }
    private static String money(Object n){
        if(n==null) return "0.00";
        java.math.BigDecimal bd = new java.math.BigDecimal(n.toString());
        return bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
    private static String formatFecha(java.util.Date d){
        if (d==null) return "";
        return new java.text.SimpleDateFormat("dd/MM/yyyy").format(d);
    }
    private static String formatFecha(java.sql.Date d){
        if (d==null) return "";
        return new java.text.SimpleDateFormat("dd/MM/yyyy").format(d);
    }
    private static String formatFechaHora(java.util.Date d){
        if (d==null) return "";
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(d);
    }
}
