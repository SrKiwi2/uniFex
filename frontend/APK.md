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

## Tráfico sin cifrar (http://) — por qué hizo falta configurarlo

El servidor de la feria es `http://virtual.uap.edu.bo:8070`, **sin TLS**. Desde Android 9
(API 28) el tráfico sin cifrar está prohibido por defecto, y este proyecto compila contra
API 36. Sin permitirlo explícitamente, **todas** las peticiones del APK fallan —el login el
primero— con `ERR_CLEARTEXT_NOT_PERMITTED` en Logcat; dentro de la app no se ve un error
claro, solo parece que el servidor no contesta.

Se resolvió con dos archivos y un atributo:

- `android/app/src/main/res/xml/network_security_config.xml` — autoriza **solo**
  `virtual.uap.edu.bo`, `10.0.2.2` (el anfitrión visto desde el emulador) y `localhost`. Es
  lo que va en un APK de **release**: el resto del tráfico sigue exigiendo TLS.
- `android/app/src/debug/res/xml/network_security_config.xml` — versión permisiva, que
  Android usa **solo en `assembleDebug`** porque una variante pisa los recursos del mismo
  nombre. Así, probar contra la IP de tu laptop no obliga a editar dominios y recompilar
  cada vez que cambia el wifi.
- `android:networkSecurityConfig="@xml/network_security_config"` en el `<application>` del
  manifiesto. `npx cap sync` **no** lo pisa: el manifiesto es tuyo, no lo regenera Capacitor.

Cuando el backend tenga certificado y pase a `https://`, los tres se pueden retirar.

## Compilar

Todo junto, con el script (compila, verifica que la URL quedó dentro, sincroniza y genera
el APK):

```bash
cd frontend
./build-apk.sh http://virtual.uap.edu.bo:8070
```

O paso a paso, que es lo mismo:

```bash
cd frontend

# 1. Compilar la SPA apuntando al servidor. SIN esta variable el APK queda en relativo
#    y llama a su propio contenedor: el login falla y parece un problema de red.
VITE_API_BASE=http://virtual.uap.edu.bo:8070 npm run build

# 2. Copiar los archivos al proyecto Android
npx cap sync android

# 3. Generar el APK de depuración
cd android && ./gradlew assembleDebug
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

**Comprobación que evita perder una tarde:** que la URL de verdad esté dentro del APK.

```bash
unzip -p android/app/build/outputs/apk/debug/app-debug.apk \
  assets/public/assets/index-*.js | grep -o "virtual.uap.edu.bo:8070" | head -1
```

Si eso no imprime nada, el APK está compilado en relativo y no va a poder ni entrar.

## Abrirlo en Android Studio

```bash
cd frontend && npx cap open android
```

En esta máquina Android Studio está instalado como **snap**, y Capacitor busca en las rutas
estándar, así que puede no encontrarlo. Si falla:

```bash
CAPACITOR_ANDROID_STUDIO_PATH=/snap/bin/android-studio npx cap open android
```

O directamente: abrir Android Studio y elegir la carpeta `frontend/android` (esa, no la raíz
del repositorio). Dentro, el APK se genera con **Build → Build App Bundle(s)/APK(s) → Build
APK(s)**.

Ojo con el orden: Android Studio compila lo que hay en `android/app/src/main/assets/public`,
que es una **copia**. Si tocas la SPA, hay que rehacer `npm run build` + `npx cap sync
android` antes de compilar en Studio, o seguirás viendo la versión anterior.

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

## Notificaciones con la app cerrada (FCM) — cómo se monta

Hoy los avisos viajan por **WebSocket** (`/topic/notificaciones/{userId}`), y eso tiene un
límite duro: **solo llegan con la app abierta**. Cuando Android la manda a segundo plano
suspende el WebView y el socket se cae; con el teléfono bloqueado, más aún. Para avisar con la
app cerrada hace falta **FCM (Firebase Cloud Messaging)**, que es el único canal que Android
entrega sin que la app esté viva.

**Esto obliga a regenerar el APK** (plugin nativo + `google-services.json`), y una vez montado,
cambiar el *contenido* de los avisos ya no lo obliga.

### 1. Firebase (consola, una sola vez)

1. Crear el proyecto en <https://console.firebase.google.com>.
2. *Agregar app* → **Android**. El **nombre del paquete debe ser idéntico** al `appId` de
   `capacitor.config.json`: `bo.edu.uap.unifex`. Si no coincide, FCM rechaza el registro y no
   hay error visible en la app — solo no llega nada.
3. Descargar **`google-services.json`** y ponerlo en **`frontend/android/app/google-services.json`**
   (ahí exactamente, junto al `build.gradle` del módulo `app`, no en la raíz).
4. En *Configuración del proyecto → Cuentas de servicio*, generar una **clave privada** (JSON).
   Ese archivo es el que usa el **backend** para enviar. **No se versiona**: va por variable de
   entorno como el resto de secretos (ver `DEPLOY.md`).

### 2. Android (proyecto nativo)

En `frontend/android/build.gradle` (el de nivel raíz), dentro de `dependencies` del bloque
`buildscript`:

```gradle
classpath 'com.google.gms:google-services:4.4.2'
```

En `frontend/android/app/build.gradle`, **al final del archivo**:

```gradle
apply plugin: 'com.google.gms.google-services'
```

En `AndroidManifest.xml` (`frontend/android/app/src/main/AndroidManifest.xml`), dentro de
`<manifest>`, el permiso que Android 13+ **exige** para poder notificar:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

> Sin ese permiso, en Android 13 o superior la app **no muestra ninguna notificación** y no
> falla: simplemente no aparece nada. Es el motivo más común de "no me llegan".

### 3. Cliente (SPA)

```bash
npm --prefix frontend install @capacitor/push-notifications
npx --prefix frontend cap sync android
```

Al iniciar sesión (en `stores/auth.js`, tras guardar el token, o en `AppLayout.vue` al montar):

```js
import { PushNotifications } from '@capacitor/push-notifications';

// 1. Pedir permiso (en Android 13+ abre el diálogo del sistema)
const permiso = await PushNotifications.requestPermissions();
if (permiso.receive !== 'granted') return;

// 2. Registrar el dispositivo en FCM
await PushNotifications.register();

// 3. FCM devuelve el token del dispositivo -> hay que MANDARLO AL BACKEND y guardarlo
//    contra el usuario. Sin esto el servidor no sabe a qué teléfono avisar.
PushNotifications.addListener('registration', (t) => {
  apiFetch('/api/app/dispositivos', { method: 'POST', body: JSON.stringify({ token: t.value }) });
});

// 4. Aviso recibido con la app ABIERTA (Android no lo muestra solo en ese caso)
PushNotifications.addListener('pushNotificationReceived', (n) => toast(n.title ?? 'Aviso', 'info'));

// 5. El usuario tocó la notificación: llevarlo a donde corresponde
PushNotifications.addListener('pushNotificationActionPerformed', (a) => {
  router.push(a.notification.data?.ruta ?? '/');
});
```

Al **cerrar sesión** hay que borrar ese token en el backend, o el siguiente usuario de ese
teléfono recibiría los avisos del anterior.

### 4. Backend (envío)

- Tabla `dispositivo` (usuario, token FCM, plataforma, última vez visto). Un usuario puede
  tener **varios** teléfonos, y un teléfono cambia de token al reinstalar: la tabla se limpia
  sola cuando FCM responde `UNREGISTERED` a un envío.
- Dependencia `com.google.firebase:firebase-admin` y un `FcmService` que envíe al token.
- **Dónde engancharlo:** en el mismo sitio donde hoy se publica por WebSocket
  (`NotificacionService`, después del commit). Es decir: se manda por los **dos** canales —
  WebSocket si la app está abierta, FCM para cuando no lo está. No se sustituye uno por otro.
- El cuerpo lleva `data` con la ruta a abrir (`{"ruta": "/mis-ventas"}`), que es lo que usa el
  listener del punto 5.

### Trampas que cuestan una tarde

- **El paquete tiene que coincidir** (`bo.edu.uap.unifex`) entre Firebase y Capacitor.
- **`POST_NOTIFICATIONS`** es obligatorio desde Android 13; sin él no se ve nada y no hay error.
- **`google-services.json` va en `android/app/`**, no en la raíz del proyecto Android.
- Los avisos **no funcionan en el navegador** con este plugin: es solo Android/iOS. En la web
  se sigue usando el WebSocket.
- Muchos teléfonos (Xiaomi, Huawei, Samsung con ahorro agresivo) **matan la app en segundo
  plano** y retrasan los avisos: hay que pedirle al vendedor que excluya la app del ahorro de
  batería. Es configuración del teléfono, no del código.
- Tras cualquier cambio en `android/`, **`npx cap sync android`** antes de compilar.

## Pendiente

- Icono y pantalla de arranque propios (ahora son los de Capacitor).
- Firma para distribución (`assembleRelease` + keystore); el `assembleDebug` de aquí sirve
  para probar, no para repartir.
- Probar el comportamiento con mala señal: el wifi de la feria es irregular y el carrito
  aguanta 12 h en el servidor, pero conviene comprobar la reconexión del WebSocket.
