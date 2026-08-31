# Empaquetar la SPA como APK Android (Capacitor)

**Estado: no iniciado.** Es la Fase 4 del plan. Esta guía describe el camino acordado y los
puntos donde la app móvil rompe supuestos que la web no rompe. Antes de ejecutarla, confirma con
el usuario que quiere empezarla.

## La idea

Un solo código Vue produce web y app. Capacitor envuelve el `dist/` de Vite en una WebView
Android nativa. No hay segunda base de código, ni Ionic, ni reescritura de las vistas.

```bash
npm --prefix frontend install @capacitor/core @capacitor/cli @capacitor/android
npm --prefix frontend exec cap init uniFex bo.edu.uap.unifex --web-dir=dist
npm --prefix frontend run build
npm --prefix frontend exec cap add android
npm --prefix frontend exec cap sync
npm --prefix frontend exec cap open android   # abre Android Studio
```

Cada vez que cambia el frontend: `npm run build && npx cap sync`. Olvidarlo hace que la APK
muestre la versión anterior, y es la causa número uno de "el cambio no aparece en el celular".

## Lo que se rompe: no hay proxy de Vite

En desarrollo, el navegador pide `/api/...` al mismo origen (`localhost:5173`) y Vite lo reenvía
al backend. **Dentro de la APK no existe ese proxy.** La WebView sirve desde `https://localhost`
o `capacitor://localhost`, así que toda ruta relativa apunta a la nada.

De ahí se siguen tres cosas:

1. **Las URLs deben volverse absolutas y configurables.** Introduce una base de API
   (`import.meta.env.VITE_API_BASE`, vacía en web y `https://…` en la build de la APK) y úsala en
   `src/api.js`, en `stores/auth.js` (que hoy llama a `fetch('/api/auth/login')` directo, sin
   pasar por `apiFetch`) y en `src/ws.js` (`new SockJS('/ws')`). Son los tres únicos lugares donde
   se construyen URLs; centralizarlos ahí es lo que hace este cambio barato.

2. **Aparece CORS**, que en dev no existía. El backend tendrá que permitir el origen de la
   WebView (`capacitor://localhost`, `http://localhost`) para `/api/**` y para el handshake de
   `/ws`. Configúralo en la cadena API, no globalmente.

3. **HTTP en claro está prohibido por defecto** en Android desde API 28. Contra un backend
   `http://` de pruebas hay que habilitar `android:usesCleartextTraffic` o una
   `network_security_config`; contra producción, usa HTTPS y no toques nada.

## El WebSocket en móvil

SockJS y STOMP funcionan en la WebView, pero la app se suspende al pasar a segundo plano y la
conexión muere sin aviso. `crearClientePuestos` ya trae `reconnectDelay: 3000`, lo que cubre la
reconexión automática, pero **al reconectar el cliente se perdió los mensajes de ese intervalo**.

Por eso, al volver al primer plano hay que **recargar el estado completo** (`GET /api/app/puestos`)
además de reconectar. Si no, el vendedor ve el mapa como estaba cuando bloqueó el teléfono, y ese
es exactamente el escenario en el que vende una caseta que otro ya vendió. Engancha
`App.addListener('resume', …)` de `@capacitor/app` a una recarga.

Es el mismo razonamiento del invariante 2 (todo cambio se difunde) llevado al caso en que el
cliente no estaba escuchando: si pudo perderse un evento, hay que resincronizar.

## El TTL de la reserva y la realidad del celular

La reserva dura 300 s. En un celular con mala señal, ese margen se consume en la propia latencia.
Antes de subir el TTL, mide: `unifex.reserva.ttl-segundos` es una propiedad, y el barrido de
expiración (`PuestoReservaScheduler`) no necesita cambios.

Muestra el tiempo restante al vendedor (`reservaExpira` ya viaja en `PuestoEstadoDTO`). Una reserva
que expira en silencio mientras el usuario llena un formulario es una mala experiencia que el
backend ya te dio los datos para evitar.

## Otros detalles del empaquetado

- **Área segura**: la barra de estado de Android se superpone a la WebView. `@capacitor/status-bar`
  y padding con `env(safe-area-inset-top)`.
- **Botón atrás**: sin manejarlo, cierra la app en lugar de navegar hacia atrás. Enlaza el
  listener `backButton` al router de Vue.
- **Orientación**: el plano se lee mucho mejor en horizontal. Considera fijarla o al menos probar
  el `focus` de `PanZoom` en ambas.
- **`localStorage` persiste** entre ejecuciones en la WebView, así que la sesión JWT sobrevive al
  cierre de la app. Verifica el vencimiento del token al arrancar, o el usuario verá el mapa un
  instante y luego un 401.
- Firmar la APK requiere un keystore. Guárdalo **fuera del repositorio**.

## Verificación

Un emulador no reproduce el problema interesante (la suspensión y la reconexión). Prueba en un
dispositivo real: reserva una caseta, bloquea la pantalla un minuto, desbloquea y comprueba que el
mapa refleja lo que pasó mientras tanto —incluidas las reservas hechas desde el navegador de
escritorio en ese intervalo.
