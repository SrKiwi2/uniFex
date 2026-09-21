/**
 * Formatos compartidos por las pantallas de Control de ventas.
 *
 * es-BO: el punto agrupa y la coma separa decimales, igual que el Excel y el PDF del servidor,
 * para que una cifra se lea igual en pantalla y en papel.
 */

export const bs = (n) => Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const ent = (n) => Number(n || 0).toLocaleString('es-BO');
export const pct = (n) => `${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} %`;

/** Fecha de un LocalDate ("2026-09-10") o LocalDateTime; con hora si se pide. */
export function fecha(v, conHora = false) {
  if (!v) return '—';
  const d = new Date(v.length === 10 ? `${v}T00:00:00` : v);
  if (Number.isNaN(d.getTime())) return v;
  const dia = d.toLocaleDateString('es-BO', { day: '2-digit', month: '2-digit', year: 'numeric' });
  if (!conHora || v.length === 10) return dia;
  return `${dia} ${d.toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' })}`;
}

/*
 * Estado de una venta según su comprobante → texto y clase de insignia. Siempre con TEXTO: el
 * color solo acompaña. Rendida = tiene su comprobante adjunto (banco y número no hacen falta).
 */
export const ESTADOS = {
  PAGADO: { texto: 'Con comprobante', clase: 'badge-ok' },
  PARCIAL: { texto: 'Comprobante parcial', clase: 'badge-parcial' },
  PENDIENTE: { texto: 'Sin comprobante', clase: 'badge-danger' },
  SIN_COSTO: { texto: 'Sin costo', clase: 'badge-muted' },
};

/** Lee el cuerpo {ok, mensaje} de una respuesta, sea cual sea el código. */
export async function resultado(r) {
  const cuerpo = await r.json().catch(() => ({}));
  if (!r.ok) {
    const e = new Error(cuerpo.mensaje || (r.status === 403 ? 'No tienes permiso para hacer esto' : 'No se pudo guardar'));
    e.status = r.status;
    throw e;
  }
  return cuerpo;
}
