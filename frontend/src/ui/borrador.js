/**
 * Borrador local del formulario de venta.
 *
 * Las casetas ya sobreviven solas: quedan reservadas en la base a nombre del vendedor
 * durante 12 h (ver stores/puestos.js). Lo que NO sobrevive por si mismo es lo que el
 * vendedor va tecleando —entidad, responsables—, y perder eso a mitad de una venta,
 * delante del cliente, es lo que hace que un sistema se sienta poco fiable.
 *
 * Se guarda en localStorage y no en el servidor a proposito: asi funciona **sin conexion**,
 * que es justo lo que hara falta en el APK con el wifi irregular de la feria.
 *
 * La clave incluye el id del vendedor: en un dispositivo compartido, el borrador de uno no
 * puede aparecerle a otro.
 */
const PREFIJO = 'unifex.borrador.venta.';
/** Un borrador muy viejo es basura, no trabajo pendiente. */
const VIGENCIA_MS = 24 * 60 * 60 * 1000;

const clave = (idUsuario) => `${PREFIJO}${idUsuario ?? 'anon'}`;

export function guardarBorrador(idUsuario, datos) {
  try {
    localStorage.setItem(clave(idUsuario), JSON.stringify({ guardadoEn: Date.now(), datos }));
  } catch (_) {
    // Cuota llena o modo privado: perder el borrador no debe romper la venta.
  }
}

/** Devuelve el borrador guardado, o null si no hay o ya caduco. */
export function leerBorrador(idUsuario) {
  try {
    const crudo = localStorage.getItem(clave(idUsuario));
    if (!crudo) return null;
    const { guardadoEn, datos } = JSON.parse(crudo);
    if (!guardadoEn || Date.now() - guardadoEn > VIGENCIA_MS) {
      borrarBorrador(idUsuario);
      return null;
    }
    return datos;
  } catch (_) {
    return null;
  }
}

export function borrarBorrador(idUsuario) {
  try {
    localStorage.removeItem(clave(idUsuario));
  } catch (_) {
    /* ignorar */
  }
}
