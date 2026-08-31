# APK (Capacitor) — UniFex

La SPA se empaqueta como aplicación Android sin cambiar de código: es **la misma app**, no
una versión aparte. Lo único distinto es a dónde llama.

## Por qué hace falta configurar la URL del servidor

En la web todo va en **relativo** (`/api/app/puestos`): en desarrollo lo proxya Vite y en
producción lo sirve el propio Spring, así que no hay CORS ni URL que mantener.

Dentro del APK eso no vale. Capacitor sirve la app desde su propio origen
(`https://localhost` en Android), así que una ruta relativa apuntaría **al contenedor de la
app**, no al servidor — y no fallaría con un error claro, simplemente no encontraría nada.
Por eso la compilación del APK necesita una URL absoluta.

Y ojo: **`localhost` dentro del teléfono es el teléfono**. Hay que usar la IP de la máquina
en la red local, y que ambos estén en la misma red.

Lo resuelve [`src/config.js`](src/config.js): con `VITE_API_BASE` definida usa esa base; sin
ella, se queda en relativo (comportamiento de siempre en la web).

## Compilar

```bash
# 1. Averigua la IP de tu máquina en la red local (Windows: ipconfig)
#    y compila la SPA apuntando ahí
VITE_API_BASE=http://192.168.20.145:7676 npm run build

# 2. Copia los archivos al proyecto Android
npx cap sync android

# 3. Genera el APK de depuración
cd android && ./gradlew assembleDebug
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

## Instalarlo en el teléfono

Con el cable y depuración USB activada:

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb" install -r \
  android/app/build/outputs/apk/debug/app-debug.apk
```

O copia el `.apk` al teléfono y ábrelo (hay que permitir «instalar apps de origen
desconocido»).

## Requisitos y trampas de esta máquina

- **`compileSdk` = 36, no 35.** Capacitor genera 35 por defecto, pero aquí solo están
  instaladas las plataformas `android-36` y `36.1` y **no hay `cmdline-tools`**, así que
  Gradle no puede descargar la 35 y la compilación fallaría pidiendo un SDK inobtenible.
  Está fijado en [`android/variables.gradle`](android/variables.gradle).
- **`local.properties`** apunta al SDK (`sdk.dir`). No se versiona (está en `.gitignore`),
  así que **cada máquina debe crear el suyo**. No hay `ANDROID_HOME` definido en el sistema.
- **`cleartext: true`** en `capacitor.config.json` permite hablar con el backend por HTTP
  plano. Android lo bloquea por defecto. Sirve para probar en red local; **para producción
  hay que servir el backend por HTTPS y quitarlo**.
- El backend acepta el origen del APK vía CORS (`SecurityConfig.corsApi()`). Si cambias el
  esquema o el puerto, hay que añadirlo ahí.

## Cómo probar que funciona

1. Levanta el backend con perfil `dev` y **que escuche en la red**, no solo en localhost.
2. Desde el teléfono, en el navegador, abre `http://<IP>:7676/api/app/puestos`: debe
   responder un 401 en JSON. Si no llega, es cortafuegos o red, no la app.
3. Abre el APK e inicia sesión. Si el login pasa pero el mapa no se pinta en vivo, mira el
   WebSocket: `config.js` deriva `ws://` de la misma base.

## Lo que el APK aporta sobre la web

- **Cámara nativa** para el comprobante y las fotos de caseta (el `<input type="file">` ya
  abre la cámara; si hace falta más control, se añade `@capacitor/camera`).
- Acceso directo desde el escritorio del teléfono, sin escribir una URL.

## Pendiente

- Icono y pantalla de arranque propios (ahora son los de Capacitor).
- Firma para distribución (`assembleRelease` + keystore); el `assembleDebug` de aquí sirve
  para probar, no para repartir.
- Probar el comportamiento con mala señal: el wifi de la feria es irregular y el carrito
  aguanta 12 h en el servidor, pero conviene comprobar la reconexión del WebSocket.
