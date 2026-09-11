#!/usr/bin/env bash
#
# Respaldos de la base de UniFex: crear, listar y restaurar.
#
# Existe para que sacar un respaldo antes de tocar nada sea UN comando y no una
# excusa para saltarselo. Los scripts R* borran datos de verdad y no se pueden
# deshacer; lo unico que separa un reinicio de una perdida es el respaldo previo.
#
# Usa el formato "custom" de PostgreSQL (-Fc), no un .sql de texto:
#   * ocupa mucho menos (viene comprimido);
#   * pg_restore puede restaurarlo entero o solo una tabla;
#   * no depende de que el .sql se pegue bien en una consola.
#
# CONEXION: por las variables estandar de psql, igual que aplicar-migraciones.sh
#   export PGHOST=virtual.uap.edu.bo PGPORT=5432 PGUSER=postgres PGDATABASE=fexpouapv2
#   export PGPASSWORD=...
#
# USO
#   ./respaldo.sh crear [etiqueta]     respaldo nuevo (la etiqueta va en el nombre)
#   ./respaldo.sh listar               los respaldos que hay, del mas nuevo al mas viejo
#   ./respaldo.sh restaurar <archivo>  DEJA la base como estaba en ese respaldo
#
# EJEMPLO, el dia que quieras limpiar produccion:
#   ./respaldo.sh crear antes-de-limpiar
#   psql -v ON_ERROR_STOP=1 -f R3__reiniciar_ventas.sql
#   ./respaldo.sh crear base-limpia          <-- este es el respaldo que querias
#
# Donde se guardan: en ~/unifex-respaldos, FUERA del proyecto, para que no acaben
# en git por accidente (llevan datos reales de personas).

set -euo pipefail

DIR_RESPALDOS="${UNIFEX_RESPALDOS:-$HOME/unifex-respaldos}"
mkdir -p "$DIR_RESPALDOS"

base_actual() { psql -tAc 'select current_database()'; }

case "${1:-}" in
  crear)
    ETIQUETA="${2:-manual}"
    BASE="$(base_actual)"
    ARCHIVO="$DIR_RESPALDOS/${BASE}_$(date +%Y%m%d-%H%M%S)_${ETIQUETA}.dump"
    echo "== Respaldando $BASE en $ARCHIVO =="
    # --no-owner y --no-privileges: asi el respaldo se puede restaurar en otra
    # maquina aunque ahi los roles de PostgreSQL se llamen distinto.
    pg_dump -Fc --no-owner --no-privileges -f "$ARCHIVO"
    # Se comprueba que el archivo se puede leer; un respaldo que no abre no es respaldo.
    if pg_restore --list "$ARCHIVO" >/dev/null 2>&1; then
      echo "== Listo: $(du -h "$ARCHIVO" | cut -f1) — verificado, se puede restaurar =="
    else
      echo "!! El archivo se creo pero pg_restore NO puede leerlo. NO te fies de el." >&2
      exit 1
    fi
    ;;

  listar)
    echo "== Respaldos en $DIR_RESPALDOS =="
    ls -lht "$DIR_RESPALDOS"/*.dump 2>/dev/null | awk '{print "  " $5 "\t" $6, $7, $8 "\t" $9}' \
      || echo "  (todavia no hay ninguno)"
    ;;

  restaurar)
    ARCHIVO="${2:?Uso: ./respaldo.sh restaurar <archivo.dump>}"
    [ -f "$ARCHIVO" ] || { echo "No existe $ARCHIVO" >&2; exit 1; }
    BASE="$(base_actual)"
    echo "⚠ Esto va a DEJAR la base '$BASE' exactamente como estaba en:"
    echo "    $ARCHIVO"
    echo "  Todo lo que haya pasado despues se pierde."
    read -r -p "  Escribe el nombre de la base para confirmar: " CONFIRMA
    [ "$CONFIRMA" = "$BASE" ] || { echo "Cancelado."; exit 1; }
    # --clean --if-exists borra los objetos antes de recrearlos; sin eso, restaurar
    # sobre una base con datos deja una mezcla de las dos, que es peor que cualquiera.
    pg_restore --clean --if-exists --no-owner --no-privileges -d "$BASE" "$ARCHIVO"
    echo "== Restaurado =="
    ;;

  *)
    sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
