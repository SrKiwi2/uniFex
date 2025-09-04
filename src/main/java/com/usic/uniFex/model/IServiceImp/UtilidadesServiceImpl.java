package com.usic.uniFex.model.IServiceImp;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.UtilidadesService;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import javax.sql.DataSource;

@Service
public class UtilidadesServiceImpl implements UtilidadesService {
  String secretKey = "Lanza12310099812"; // La clave debe tener 16, 24 o 32 caracteres para AES-128, AES-192 o
  // AES-256 respectivamente
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  private DataSource dataSource;

  @Override
  public String decrypt(String encryptedText) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
    byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
    byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
    return new String(decryptedBytes, StandardCharsets.UTF_8);
  }

  @Override
  public String encrypt(String data) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
    byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  @Override
  public ByteArrayOutputStream compilarAndExportarReporte(String nombreArchivo, Map<String, Object> params)
      throws IOException, JRException, SQLException {
    Connection con = null;

    // return stream;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    Path rootPath = Paths.get("").toAbsolutePath();
    Path directorio = Paths.get(rootPath.toString(), "reportes", nombreArchivo);
    String ruta = directorio.toString();
        System.out.println(ruta);
    try (InputStream reportStream = new FileInputStream(ruta)) {
      con = dataSource.getConnection();

      JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

      JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, con);
      JasperExportManager.exportReportToPdfStream(jasperPrint, stream);
    } catch (IOException | JRException | SQLException e) {
      System.out.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    }
    con.close();
    return stream;
  }

  @Override
  public ByteArrayOutputStream compilarAndExportarReporteExcel(String nombreArchivo, Map<String, Object> params)
      throws IOException, JRException, SQLException {
    Connection con = null;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    Path rootPath = Paths.get("").toAbsolutePath();
    Path directorio = Paths.get(rootPath.toString(), "reportes", nombreArchivo);
    String ruta = directorio.toString();

    try (InputStream reportStream = new FileInputStream(ruta)) {
      con = dataSource.getConnection();

      JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
      JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, con);

      // Exportar a Excel
      JRXlsxExporter exporter = new JRXlsxExporter();
      exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(stream));

      exporter.exportReport();

    } catch (IOException | JRException | SQLException e) {
      System.out.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    } finally {
      if (con != null) {
        con.close();
      }
    }

    return stream;
  }

  @Override
  public ByteArrayOutputStream compilarAndExportarReporteWord(String nombreArchivo, Map<String, Object> params)
      throws IOException, JRException, SQLException {
    Connection con = null;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    Path rootPath = Paths.get("").toAbsolutePath();
    Path directorio = Paths.get(rootPath.toString(), "reportes", nombreArchivo);
    String ruta = directorio.toString();

    try (InputStream reportStream = new FileInputStream(ruta)) {
      con = dataSource.getConnection();

      JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
      JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, con);

      // Exportar a RTF
      JRDocxExporter exporter = new JRDocxExporter();
      exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(stream));

      exporter.exportReport();

    } catch (IOException | JRException | SQLException e) {
      System.out.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    } finally {
      if (con != null) {
        con.close();
      }
    }

    return stream;
  }

  @Override
  public ByteArrayOutputStream compilarAndExportarReporteExcelSinDataBase(String nombreArchivo,
      Map<String, Object> params, JRDataSource dataSource) throws IOException, JRException, SQLException {
    Connection con = null;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    Path rootPath = Paths.get("").toAbsolutePath();
    Path directorio = Paths.get(rootPath.toString(), "reportes", nombreArchivo);
    String ruta = directorio.toString();

    try (InputStream reportStream = new FileInputStream(ruta)) {
      // Si se necesita una conexión de base de datos
      if (dataSource == null) {
        con = this.dataSource.getConnection(); // Aquí asumo que tienes un dataSource para conexiones a DB
        // Llenar el reporte usando la conexión
        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, con);

        // Exportar a Excel
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(stream));

        exporter.exportReport();
      } else {
        // Llenar el reporte usando el JRDataSource
        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);

        // Exportar a Excel
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(stream));

        exporter.exportReport();
      }

    } catch (IOException | JRException | SQLException e) {
      System.out.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    } finally {
      if (con != null) {
        con.close();
      }
    }

    return stream;
  }

  @Override
  public String guardarArchivo(MultipartFile archivo) {
    String uniqueFilename = UUID.randomUUID().toString() + "_" + archivo.getOriginalFilename();

		
    Path rootPath = Paths.get("uploads/").resolve(uniqueFilename);
		Path rootAbsolutPath = rootPath.toAbsolutePath();

		System.out.println("LA DIRECCION ES: " + rootAbsolutPath);
		log.info("rootPath: " + rootPath);
		log.info("rootAbsolutPath: " + rootAbsolutPath);

		try {
			System.out.println("CUARDAR EN EL DIRECCTORIO");
      Path parentDir = rootAbsolutPath.getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            System.out.println("Directorio creado: " + parentDir.toString());
        }
			Files.copy(archivo.getInputStream(), rootAbsolutPath);
      System.out.println("Archivo guardado correctamente en: " + rootAbsolutPath);

		} catch (IOException e) {
			System.out.println("ERROR AL GUARDAR EL ARCHIVO: " + e.getMessage());
			e.printStackTrace();
		}

		return uniqueFilename;
  }
}
