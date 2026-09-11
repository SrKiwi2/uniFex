#!/usr/bin/env bash
#
# Aplica en orden los scripts de esquema V1..V23 de este directorio contra la base
# indicada por las variables de entorno estandar de psql (PGHOST, PGPORT, PGUSER,
# PGPASSWORD, PGDATABASE), registrando en la tabla de control public._migraciones_aplicadas
# cuales ya corrieron para no repetirlas ni saltarse ninguna.
#
# NUNCA aplica R1__reset_gestion.sql: ese script BORRA todo lo transaccional (casetas,
# entidades, inscripciones, ventas...) y su propia cabecera dice "Nunca contra produccion".
# Es una herramienta de desarrollo, no una migracion; se ejecuta a mano, aparte, solo si
# se sabe exactamente lo que se esta haciendo.
#
# Por que existe este script en vez de aplicar los V*.sql a mano con psql -f uno por uno:
# este proyecto no usa Flyway/Liquibase a proposito (ver CLAUDE.md), asi que no hay ningun
# registro de que scripts ya se aplicaron a una base dada. Sin ese registro es facil
# aplicar un script dos veces, saltarse uno, o aplicarlos fuera de orden -- y varios de
# estos scripts no son idempotentes (V7, por ejemplo, no tiene guardas IF NOT EXISTS).
# La tabla de control es la unica pieza que le faltaba al proceso manual para ser seguro,
# sin traer un framework nuevo al proyecto.
#
# Uso normal (aplica todo lo pendiente, en orden):
#   export PGHOST=localhost PGPORT=5432 PGUSER=postgres PGDATABASE=v2_fexpo_uap
#   export PGPASSWORD=...
#   ./aplicar-migraciones.sh
#
# Si la base de destino YA tiene aplicados a mano algunos V*.sql de antes de que existiera
# este script (por ejemplo, produccion), hay que marcarlos como aplicados SIN ejecutarlos,
# uno por uno y en orden, antes de correr el modo normal:
#   ./aplicar-migraciones.sh --marcar-aplicada V1__reserva_puesto.sql
#
# Verificar que estado quedo (que se aplico y cuando), sin aplicar nada:
#   ./aplicar-migraciones.sh --estado

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

crear_tabla_control() {
  psql -v ON_ERROR_STOP=1 -c "
    create table if not exists public._migraciones_aplicadas (
      nombre       text primary key,
      aplicada_en  timestamptz not null default now()
    );" >/dev/null
}

if [ "${1:-}" = "--estado" ]; then
  crear_tabla_control
  psql -c "select nombre, aplicada_en from public._migraciones_aplicadas order by aplicada_en;"
  exit 0
fi

if [ "${1:-}" = "--marcar-aplicada" ]; then
  nombre="${2:?Uso: --marcar-aplicada NOMBRE_DEL_ARCHIVO.sql}"
  if [ ! -f "$DIR/$nombre" ]; then
    echo "No existe $DIR/$nombre" >&2
    exit 1
  fi
  crear_tabla_control
  psql -v ON_ERROR_STOP=1 -c "
    insert into public._migraciones_aplicadas(nombre) values ('$nombre')
    on conflict (nombre) do nothing;"
  echo "Marcada como ya aplicada (sin ejecutar): $nombre"
  exit 0
fi

echo "== Verificando conexion a $(psql -tAc 'select current_database()') =="
crear_tabla_control

aplicadas=0
saltadas=0
while IFS= read -r archivo; do
  nombre="$(basename "$archivo")"
  ya=$(psql -tAc "select 1 from public._migraciones_aplicadas where nombre = '$nombre'")
  if [ "$ya" = "1" ]; then
    echo "-- $nombre ya aplicada, se salta"
    saltadas=$((saltadas + 1))
    continue
  fi
  echo "== Aplicando $nombre =="
  psql -v ON_ERROR_STOP=1 -f "$archivo"
  psql -v ON_ERROR_STOP=1 -c "insert into public._migraciones_aplicadas(nombre) values ('$nombre');" >/dev/null
  echo "-- $nombre aplicada y registrada"
  aplicadas=$((aplicadas + 1))
done < <(printf '%s\n' "$DIR"/V*.sql | sort -V)

echo "== Listo: $aplicadas aplicadas, $saltadas ya estaban =="
