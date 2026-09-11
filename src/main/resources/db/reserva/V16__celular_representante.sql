-- =============================================================================
-- V16 — Celular del responsable legal de la entidad.
--
-- El registro de una venta pedia el representante legal y su C.I., pero no un
-- telefono, y el dato del DUEÑO real de la caseta es justo el que hace falta
-- cuando hay que llamar por un cobro, un cambio de puesto o una incidencia en
-- la feria. Los celulares que si se guardaban eran los de los responsables que
-- atienden el puesto, que muchas veces son terceros.
--
-- Una sola columna, opcional en la base. La obligatoriedad se aplica en el
-- registro nuevo de la SPA (RegistroVentaService.validar), no con un NOT NULL:
-- las entidades ya registradas no lo tienen y ponerlo obligatorio en la base
-- dejaria el historico sin poder tocarse.
--
-- Es idempotente: volver a ejecutarlo no cambia nada.
--
-- Ejecutar:
--   psql -h <host> -U postgres -d <base> -f V16__celular_representante.sql
-- =============================================================================

ALTER TABLE entidad ADD COLUMN IF NOT EXISTS celular_representante varchar(30);

COMMENT ON COLUMN entidad.celular_representante IS
  'Telefono del responsable legal (el dueño). Obligatorio en altas nuevas desde la SPA.';
