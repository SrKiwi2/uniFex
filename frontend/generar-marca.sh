#!/usr/bin/env bash
#
# Genera los iconos del APK y las imagenes de marca de la web a partir de los dos originales
# de `imagenes/`. Se ejecuta A MANO, cuando cambie el logo; no forma parte del build.
#
#   cd frontend && ./generar-marca.sh
#
# Por que un script y no archivos sueltos commiteados sin mas: son 26 archivos derivados de
# dos originales, y regenerarlos a ojo cuando cambie el logo es la clase de tarea que sale
# mal. Aqui queda escrito de donde sale cada tamaño y por que.
#
# Necesita ImageMagick (`convert`).
set -euo pipefail
cd "$(dirname "$0")"

LOGO=imagenes/logoFexpoUapV2.png       # el logotipo apaisado, sobre fondo negro opaco
ICONO=imagenes/iconoFexpoUapV2Apk.png  # el badge cuadrado, pensado como icono de app
RES=android/app/src/main/res
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

command -v convert >/dev/null || { echo "Falta ImageMagick (convert)"; exit 1; }

# Los dos originales traen el fondo NEGRO pegado (son RGB sin canal alfa). Para poder
# ponerlos sobre cualquier fondo hay que recuperarlo como transparencia. El 12% de tolerancia
# esta medido: el verde mas oscuro del dibujo es #012308, lejos del negro, asi que no se come
# nada del jaguar ni del contorno.
convert "$LOGO"  -fuzz 12% -transparent black -trim +repage "$TMP/logo.png"
convert "$ICONO" -fuzz 12% -transparent black -trim +repage "$TMP/badge.png"

# ---------------------------------------------------------------- iconos del lanzador
# Android pinta el icono adaptativo recortado con la forma que elija el fabricante (circulo,
# squircle, gota...). Solo el 66% central esta garantizado, asi que el dibujo va al 62% y el
# resto es margen de seguridad: pasarse de ahi es como acaban los logos con las letras cortadas.
for par in "mdpi 108" "hdpi 162" "xhdpi 216" "xxhdpi 324" "xxxhdpi 432"; do
  set -- $par; d=$1; s=$2; dentro=$(( s * 62 / 100 ))
  mkdir -p "$RES/mipmap-$d"
  convert -size "${s}x${s}" xc:none \
    \( "$TMP/logo.png" -resize "${dentro}x${dentro}" \) -gravity center -composite \
    "$RES/mipmap-$d/ic_launcher_foreground.png"
done

# Iconos "legacy", para Android 7 y anteriores: el badge tal cual, con las esquinas ya
# transparentes, que es como se diseño.
for par in "mdpi 48" "hdpi 72" "xhdpi 96" "xxhdpi 144" "xxxhdpi 192"; do
  set -- $par; d=$1; s=$2
  convert "$TMP/badge.png" -resize "${s}x${s}" -background none -gravity center \
    -extent "${s}x${s}" "$RES/mipmap-$d/ic_launcher.png"

  # El redondo NO es el badge recortado en circulo: un cuadrado redondeado dentro de un
  # circulo deja cuatro medias lunas vacias. Se dibuja el circulo con el degradado verde del
  # propio badge y encima el logotipo.
  r=$(( s / 2 )); dentro=$(( s * 82 / 100 ))
  # Degradado RADIAL y no lineal: le da volumen al circulo, y con el centro mas claro el
  # logotipo —que tambien es verde— no se funde con el fondo.
  convert -size "${s}x${s}" radial-gradient:'#14A83A'-'#04250B' \
    \( -size "${s}x${s}" xc:none -fill white -draw "circle $r,$r $r,0" -alpha copy \) \
    -compose copyopacity -composite \
    \( "$TMP/logo.png" -resize "${dentro}x${dentro}" \) -gravity center -compose over -composite \
    "$RES/mipmap-$d/ic_launcher_round.png"
done

# El fondo del icono adaptativo: verde oscuro del propio badge, para que el logotipo (verde
# claro) resalte. En blanco, que es como venia por defecto, el logo se perdia.
cat > "$RES/values/ic_launcher_background.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por generar-marca.sh. Verde oscuro tomado del propio badge del logo. -->
<resources>
    <color name="ic_launcher_background">#083D14</color>
</resources>
XML

# ---------------------------------------------------------------- splash nativo
# Es lo PRIMERO que se ve, antes de que exista el WebView. Va sobre negro porque el logotipo
# se diseño sobre negro y asi el paso al splash animado de la app no tiene costura.
for f in "$RES"/drawable*/splash.png; do
  [ -e "$f" ] || continue
  read -r w h <<< "$(identify -format "%w %h" "$f")"
  menor=$(( w < h ? w : h )); dentro=$(( menor * 62 / 100 ))
  convert -size "${w}x${h}" xc:black \
    \( "$TMP/logo.png" -resize "${dentro}x${dentro}" \) -gravity center -composite "$f"
done

# ---------------------------------------------------------------- web
mkdir -p public
# Para la pestaña del navegador manda el badge, no el logotipo: a 32 px un dibujo apaisado
# con letras no se lee, y un badge cuadrado si.
convert "$TMP/badge.png" -resize 32x32   -background none -gravity center -extent 32x32   public/favicon-32.png
convert "$TMP/badge.png" -resize 16x16   -background none -gravity center -extent 16x16   public/favicon-16.png
convert "$TMP/badge.png" -resize 180x180 -background none -gravity center -extent 180x180 public/apple-touch-icon.png
# El logotipo con transparencia, para la pantalla de bienvenida de la app.
convert "$TMP/logo.png" -resize 640x640 public/logo-fexpo.png

echo "Listo. Generado:"
find "$RES" -name "ic_launcher*.png" -newermt '-2 minutes' | wc -l | xargs echo "  iconos del lanzador:"
find "$RES" -name "splash.png" -newermt '-2 minutes' | wc -l | xargs echo "  splash nativos:"
ls -la public/favicon-32.png public/favicon-16.png public/apple-touch-icon.png public/logo-fexpo.png
