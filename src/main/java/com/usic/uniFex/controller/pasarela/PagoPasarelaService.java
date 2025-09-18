package com.usic.uniFex.controller.pasarela;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.usic.uniFex.model.dto.PagoRequest;

@Service
public class PagoPasarelaService {
    @Value("${pasarela.url}")
    private String urlPasarela;

    @Value("${pasarela.key}")
    private String accessKey;

    @Value("${pasarela.successUrl}") // e.g. https://tu-dominio/pagos/retorno
    private String successUrl;

    @Value("${pasarela.cancelUrl}")  // e.g. https://tu-dominio/pagos/cancelado
    private String cancelUrl;

    @Value("${pasarela.notifyUrl}")  // e.g. https://tu-dominio/api/pagos/notificacion
    private String notifyUrl;

    private final RestTemplate rest = new RestTemplate();

    public Map<String, Object> crearTransaccion(PagoRequest req) {
        // ---- datosPago ----
        Map<String, Object> datosPago = new HashMap<>();
        datosPago.put("nombresCliente",  req.getDatosCliente().getNombres());
        datosPago.put("apellidosCliente",req.getDatosCliente().getApellidos());
        datosPago.put("tipoDocumentoCliente", 1);
        datosPago.put("numeroDocumentoCliente", req.getDatosCliente().getCiNit());
        datosPago.put("fechaNacimientoCliente", req.getDatosCliente().getFechaNacimiento());

        double montoTotal = req.getItems().stream()
                .mapToDouble(i -> i.getPrecioUnitario() * i.getCantidad())
                .sum();
        datosPago.put("montoTotal", montoTotal);
        datosPago.put("correo", req.getDatosCliente().getCorreo());

        // ---- productos ----
        List<Map<String, Object>> productos = new ArrayList<>();
        for (var it : req.getItems()) {
            Map<String, Object> p = new HashMap<>();
            p.put("descripcion", it.getDescripcion());
            p.put("precioUnitario", it.getPrecioUnitario());
            p.put("montoDescuento", 0);
            p.put("cantidad", it.getCantidad());
            productos.add(p);
        }

        // ---- request raíz ----
        Map<String, Object> requests = new HashMap<>();
        requests.put("descripcion", "Compra FEXPO UAP");
        requests.put("datosPago", datosPago);
        requests.put("productos", productos);
        requests.put("successUrl", successUrl);
        requests.put("cancelUrl", cancelUrl);
        requests.put("notifyUrl", notifyUrl);
        requests.put("metadata", req.getMetadata()); // lo que necesitamos para cerrar la venta

        // ---- headers ----
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", accessKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requests, headers);

        try {
            ResponseEntity<Map> resp = rest.exchange(urlPasarela, HttpMethod.POST, entity, Map.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                Map body = resp.getBody();
                Object ok = body.get("ok");
                Object status = body.get("status");
                if (Boolean.parseBoolean(String.valueOf(ok)) && "200".equals(String.valueOf(status))) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    Map<String, Object> out = new HashMap<>();
                    out.put("codigoTransaccion", data.get("codigoTransaccion"));
                    out.put("urlRedireccion",  data.get("urlRedireccion"));
                    return out;
                } else {
                    throw new RuntimeException("Pasarela rechazó la petición: " + body.get("mensaje"));
                }
            }
            throw new RuntimeException("Respuesta inválida de pasarela");
        } catch (Exception e) {
            throw new RuntimeException("Error contactando pasarela: " + e.getMessage(), e);
        }
    }
}
