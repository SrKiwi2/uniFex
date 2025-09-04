package com.usic.uniFex.model.IService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;

public interface UtilidadesService {

        public String guardarArchivo(MultipartFile archivo);

    String decrypt(String encryptedText) throws Exception;

    String encrypt(String data) throws Exception;

    ByteArrayOutputStream compilarAndExportarReporte(String nombreArchivo, Map<String, Object> params)
            throws IOException, JRException, SQLException;

    ByteArrayOutputStream compilarAndExportarReporteExcel(String nombreArchivo, Map<String, Object> params)
            throws IOException, JRException, SQLException;

    public ByteArrayOutputStream compilarAndExportarReporteWord(String nombreArchivo, Map<String, Object> params)
            throws IOException, JRException, SQLException;

    public ByteArrayOutputStream compilarAndExportarReporteExcelSinDataBase(String nombreArchivo,
            Map<String, Object> params,
            JRDataSource dataSource)
            throws IOException, JRException, SQLException;
}
