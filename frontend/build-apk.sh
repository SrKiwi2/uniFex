#!/usr/bin/env bash
#
# Compila la SPA apuntando a un backend real, sincroniza con el proyecto Android y genera
# el APK de depuracion, en un solo comando.
#
# Por que existe: "npm run build" solo (sin VITE_API_BASE) compila en RELATIVO, que es
# correcto para la web pero rompe el APK -- todas las peticiones (login incluido) quedan
# apuntando a "https://localhost", el origen del propio WebView de Capacitor, no a tu
# servidor. El sintoma es siempre el mismo: "No se pudo iniciar sesion" y en Logcat
# "Handling local request: https://localhost/api/auth/login" en vez de una peticion real.
# Ya paso dos veces por olvidar la variable -- este script no permite olvidarla.
#
# Uso:
#   ./build-apk.sh                                    # backend en 10.0.2.2 (emulador de Android Studio)
#   ./build-apk.sh http://192.168.20.230:7676          # backend en tu IP de red, para un telefono real
#   ./build-apk.sh --instalar                          # ademas instala en el dispositivo/emulador conectado
#   ./build-apk.sh http://192.168.20.230:7676 --instalar
#
# Requiere: el backend ya levantado (mvnw spring-boot:run -Dspring-boot.run.profiles=dev)
# y, si usas --instalar, un emulador corriendo o un telefono con "adb devices" detectandolo.

set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

BASE="http://10.0.2.2:7676"
INSTALAR=false
for arg in "$@"; do
  case "$arg" in
    --instalar) INSTALAR=true ;;
    http://*|https://*) BASE="$arg" ;;
    *) echo "Argumento no reconocido: $arg" >&2; exit 1 ;;
  esac
done

echo "== Compilando con VITE_API_BASE=$BASE =="
VITE_API_BASE="$BASE" npm run build

echo "== Verificando que la IP quedo horneada en el bundle =="
if ! grep -qo "${BASE#http://}" dist/assets/index-*.js 2>/dev/null; then
  echo "AVISO: no encontre '$BASE' en el bundle compilado. Revisa antes de seguir." >&2
fi

echo "== Sincronizando con el proyecto Android =="
npx cap sync android

echo "== Generando APK debug =="
(cd android && chmod +x gradlew && ./gradlew assembleDebug)

APK="android/app/build/outputs/apk/debug/app-debug.apk"
echo "== Listo: $APK =="

if $INSTALAR; then
  ADB="$HOME/Android/Sdk/platform-tools/adb"
  [ -x "$ADB" ] || ADB="adb"
  echo "== Reinstalando en el dispositivo/emulador conectado (por si quedo cache vieja) =="
  "$ADB" uninstall bo.edu.uap.unifex || true
  "$ADB" install "$APK"
fi
