import { apiFetch } from '../api.js';
import { toast } from './toast.js';

/**
 * Bajar un PDF del servidor, en la web y en el APK.
 *
 * No se puede hacer con un `<a href>` a secas: el endpoint del recibo exige la cabecera
 * Authorization y un enlace normal no la manda, asi que caeria en un 401. Y tampoco vale
 * hacer lo mismo en los dos sitios:
 *
 *  - **Web**: se baja el PDF a un blob y se dispara un `<a download>`. El navegador lo guarda
 *    en Descargas con el nombre que le demos.
 *  - **APK**: el WebView de Android no sabe que hacer con un `blob:` ni con `<a download>`;
 *    no pasa nada y el vendedor cree que la aplicacion se colgo. Ahi se escribe el archivo
 *    con el plugin Filesystem en la carpeta Documentos del telefono, que si es visible desde
 *    el gestor de archivos y desde WhatsApp.
 *
 * El PDF NO se puede servir por una URL publica para ahorrarse todo esto: lleva C.I. y
 * telefonos de los responsables e importes, y el endpoint publico de verificacion esta hecho
 * a proposito para no revelar nada de eso.
 */
export async function descargarPdf(ruta, nombreArchivo, opciones = {}) {
  // `opciones` permite pedirlo por POST con un cuerpo: las credenciales se generan a partir
  // de una seleccion que puede ser de cientos de ids, y eso no cabe en una URL.
  const r = await apiFetch(ruta, opciones);
  if (!r.ok) {
    // El servidor explica en texto plano por que no hay documento (por ejemplo, que ninguna
    // de las credenciales pedidas cumple los requisitos). Decirlo es mas util que un generico.
    const motivo = await r.text().catch(() => '');
    throw new Error(motivo && motivo.length < 200 ? motivo : 'El servidor no devolvió el documento');
  }
  const blob = await r.blob();

  if (esNativo()) {
    return guardarEnElTelefono(blob, nombreArchivo);
  }
  guardarEnElNavegador(blob, nombreArchivo);
  return { destino: 'navegador' };
}

function esNativo() {
  return typeof window !== 'undefined'
      && window.Capacitor
      && typeof window.Capacitor.isNativePlatform === 'function'
      && window.Capacitor.isNativePlatform();
}

function guardarEnElNavegador(blob, nombreArchivo) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombreArchivo;
  document.body.appendChild(a);
  a.click();
  a.remove();
  // Sin esto el blob se queda en memoria hasta recargar la pagina.
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

async function guardarEnElTelefono(blob, nombreArchivo) {
  // El import es dinamico para que la web no cargue el plugin, que ahi no sirve de nada.
  const { Filesystem, Directory } = await import('@capacitor/filesystem');
  const datos = await aBase64(blob);
  // Documents y no Cache: en Cache el archivo es invisible para el usuario y Android lo borra
  // cuando quiere. En Documentos queda donde lo va a buscar.
  const { uri } = await Filesystem.writeFile({
    path: nombreArchivo,
    data: datos,
    directory: Directory.Documents,
    recursive: true,
  });
  return { destino: 'telefono', uri };
}

/** El plugin recibe base64, no un blob. Se le quita la cabecera `data:...;base64,`. */
function aBase64(blob) {
  return new Promise((resolve, reject) => {
    const lector = new FileReader();
    lector.onerror = () => reject(new Error('No se pudo leer el documento'));
    lector.onloadend = () => resolve(String(lector.result).split(',')[1] ?? '');
    lector.readAsDataURL(blob);
  });
}

/**
 * El recibo de una venta, con aviso al usuario. Devuelve true si se bajo.
 *
 * Es una funcion aparte y no un `descargarPdf` suelto porque el nombre del archivo y el aviso
 * tienen que ser los mismos se pida desde donde se pida: al registrar la venta o despues desde
 * "Mis ventas".
 */
export async function descargarRecibo(inscripcionId) {
  try {
    const res = await descargarPdf(`/api/app/inscripciones/${inscripcionId}/recibo`,
                                   `nota-venta-${inscripcionId}.pdf`);
    toast(res.destino === 'telefono'
      ? `Recibo guardado en Documentos (nota-venta-${inscripcionId}.pdf)`
      : 'Recibo descargado', 'ok');
    return true;
  } catch (e) {
    // La venta ya esta hecha: que falle la descarga no es motivo para alarmar, pero si para
    // decirle donde volver a pedirla.
    toast(`No se pudo descargar el recibo: ${e.message}. Puedes bajarlo desde Mis ventas.`, 'error');
    return false;
  }
}
