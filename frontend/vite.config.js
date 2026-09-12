import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// En dev, Vite (5173) hace de proxy hacia el backend Spring (7676): asi el navegador
// ve todo en el mismo origen y no hay CORS. /ws se proxya con ws:true (WebSocket nativo).
//
// El destino se puede cambiar con VITE_BACKEND, para levantar un segundo servidor de
// desarrollo contra otro backend sin tocar este archivo:
//   VITE_BACKEND=http://127.0.0.1:7677 npx vite --port 5174
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
      '/api': { target: BACKEND, changeOrigin: true },
      '/ws': { target: BACKEND, changeOrigin: true, ws: true },
      // Comprobantes y demás archivos subidos (servidos por el backend en /files/**).
      '/files': { target: BACKEND, changeOrigin: true },
    },
  },
});
