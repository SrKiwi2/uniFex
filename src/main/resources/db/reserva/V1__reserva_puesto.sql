-- Fase 1: modelo de reserva atomica de puestos (anti doble-venta)
-- Estados de puesto:
--   L = LIBRE
--   T = EN_TRAMITE  (reservado temporalmente, con caducidad reserva_expira)
--   O = OCUPADO     (venta confirmada)
--   X = bloqueado / no vendible
-- Script idempotente: se puede ejecutar varias veces sin error.

ALTER TABLE public.puesto
    ADD COLUMN IF NOT EXISTS reservado_por_id_usuario bigint,
    ADD COLUMN IF NOT EXISTS reserva_expira          timestamp without time zone,
    ADD COLUMN IF NOT EXISTS version                 bigint NOT NULL DEFAULT 0;

-- Indice de apoyo para el job que libera reservas vencidas.
CREATE INDEX IF NOT EXISTS idx_puesto_reserva_expira
    ON public.puesto (reserva_expira)
    WHERE estado_puesto = 'T';
