-- =============================================================================
-- V12 — El trigger de duplicado de id_puesto debe ignorar la baja logica.
--
-- La funcion verificar_duplicado_id_puesto (BEFORE INSERT OR UPDATE sobre
-- inscripcion_puesto) rechazaba revender una caseta que habia pertenecido a una
-- inscripcion CANCELADA: desde V10 la cancelacion es baja logica (_estado = 'X'),
-- la fila del detalle se conserva en el historico, y el trigger la contaba como
-- duplicado -> "Ya existe un registro con id_puesto=...".
--
-- La correccion filtra contra la inscripcion activa: solo cuenta como duplicado
-- lo que pertenece a una venta vigente (baja logica distinta de 'X'). Como la
-- funcion usa CREATE OR REPLACE con la misma firma, el trigger existente
-- (trg_no_duplicados_id_puesto) sigue apuntando a la version corregida.
--
-- Idempotente: CREATE OR REPLACE no falla al re-ejecutarse.
-- =============================================================================

CREATE OR REPLACE FUNCTION public.verificar_duplicado_id_puesto()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_estado_inscripcion text;
BEGIN
    -- La inscripcion a la que pertenece la fila nueva debe seguir vigente
    -- (_estado distinto de 'X'); si no, la venta esta cancelada o anulada.
    IF NEW.id_inscripcion IS NOT NULL THEN
        SELECT i._estado INTO v_estado_inscripcion
        FROM inscripcion i
        WHERE i.id = NEW.id_inscripcion;

        IF v_estado_inscripcion IS NOT NULL AND v_estado_inscripcion = 'X' THEN
            RETURN NEW;
        END IF;
    END IF;

    -- Revisamos si ya existe otro registro con el mismo id_puesto, en una
    -- inscripcion vigente (la baja logica no cuenta como duplicado).
    IF EXISTS (
        SELECT 1
        FROM inscripcion_puesto ip
        JOIN inscripcion i ON i.id = ip.id_inscripcion
        WHERE ip.id_puesto = NEW.id_puesto
          AND ip.id <> NEW.id
          AND (i._estado IS NULL OR i._estado <> 'X')
    ) THEN
        RAISE EXCEPTION 'Ya existe un registro con id_puesto=%', NEW.id_puesto;
    END IF;

    RETURN NEW;
END;
$function$
