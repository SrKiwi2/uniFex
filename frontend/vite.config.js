import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// En dev, Vite (5173) hace de proxy hacia el backend Spring (7676): asi el navegador
// ve todo en el mismo origen y no hay CORS. /ws se proxya con ws:true (WebSocket nativo).
//
// El destino se puede cambiar con VITE_BACKEND, para levantar un segundo servidor de
// desarrollo contra otro backend sin tocar este archivo:
//   VITE_BACKEND=http://127.0.0.1:7677 npx vite --port 5174
//
// OJO, esto NO tiene nada que ver con el APK. Todo lo que hay en `server` existe solo
// mientras corre `vite dev`; `npm run build` no levanta ningun servidor y lo ignora por
// completo. A donde apunta el APK lo decide VITE_API_BASE, que es OTRA variable: se lee en
// src/config.js y Vite la hornea en el bundle al compilar. Comprobado: compilar con
// VITE_BACKEND puesto a cualquier disparate da un dist byte a byte identico.
const BACKEND = process.env.VITE_BACKEND || 'http://127.0.0.1:7676';

export default defineConfig({
  plugins: [vue()],
  base: process.env.VITE_BASE || '/',
  server: {
    port: 5173,
    // `host: true` escucha en TODAS las direcciones (IPv4 e IPv6), y eso no es un detalle:
    // por defecto Vite se ataba solo a ::1, mientras que `localhost` resuelve a ::1 Y a
    // 127.0.0.1. El navegador intentaba primero 127.0.0.1 —donde no habia nadie—, esperaba
    // a que fallara y recaia en ::1. Ese reintento eran ~2 s en CADA conexion nueva: la
    // primera reserva iba lenta, las siguientes rapidas reutilizando la conexion, y volvia
    // a pasar cuando caducaba por inactividad.
    // Efecto secundario buscado: el servidor queda accesible desde la red local, que es
    // como habra que probar el APK desde un telefono.
    host: true,
    proxy: {
      // 127.0.0.1 explicito en vez de 'localhost': evita que Node vuelva a jugar a
      // adivinar entre IPv4 e IPv6 en el salto proxy -> backend.
      //
      // `xfwd: true` manda X-Forwarded-Host / -Proto / -For, y NO es un adorno.
      //
      // Con `changeOrigin: true` el proxy reescribe la cabecera Host a la del destino, asi que
      // el backend cree estar en 127.0.0.1:7676 y construye ahi sus redirecciones. Cualquier
      // peticion que caiga en la cadena web —una ruta que no existe, un /files/** que falta—
      // acaba en un 302 a la pagina de login con la direccion ABSOLUTA del backend dentro.
      // El navegador sigue esa redireccion, aterriza en OTRO origen, y lo que se ve es un
      // error de CORS en `http://127.0.0.1:7676/` que no menciona por ningun lado la peticion
      // que lo provoco. Con estas cabeceras, el ForwardedHeaderFilter que ya tiene el backend
      // reconstruye la URL original y redirige a localhost:5173, que es el mismo origen.
      '/api': { target: BACKEND, changeOrigin: true, xfwd: true },
      '/ws': { target: BACKEND, changeOrigin: true, ws: true, xfwd: true },
      // Comprobantes y demás archivos subidos (servidos por el backend en /files/**).
      '/files': { target: BACKEND, changeOrigin: true, xfwd: true },
    },
  },
});
