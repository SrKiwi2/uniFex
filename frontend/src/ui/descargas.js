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
/**
 * La credencial VIRTUAL de un responsable, como imagen.
 *
 * Va aparte de `descargarPdf` porque no es un papel: es lo que el expositor va a llevar en el
 * telefono y a enseñar en la puerta. En el movil se guarda en Documentos como PNG —visible
 * desde la galeria y adjuntable por WhatsApp, que es como se reparte— y en la web se descarga.
 */
export async function descargarCredencialVirtual(responsableId, nombre, carpeta = null) {
  const archivo = `credencial-${nombreSeguro(nombre || responsableId)}.png`;
  try {
    const res = await descargarArchivo(
      `/api/app/credenciales/${responsableId}/virtual`, archivo, { carpeta });
    toast(res.destino === 'telefono'
      ? (res.carpeta ? `Guardada en Documentos › ${res.carpeta}` : `Credencial guardada en Documentos`)
      : 'Credencial descargada', 'ok');
    return true;
  } catch (e) {
    toast(`No se pudo generar la credencial: ${e.message}`, 'error');
    return false;
  }
}

export async function descargarPdf(ruta, nombreArchivo, opciones = {}) {
  return descargarArchivo(ruta, nombreArchivo, opciones);
}

/**
 * Baja CUALQUIER archivo del servidor, en la web y en el APK.
 *
 * Antes esto se llamaba `descargarPdf` y solo se usaba para eso; el nombre se quedo corto en
 * cuanto aparecio la credencial virtual, que es un PNG. El mecanismo no tenia nada de PDF:
 * blob + `<a download>` en la web, Filesystem en el telefono.
 */
export async function descargarArchivo(ruta, nombreArchivo, opciones = {}) {
  // `carpeta` no viaja al servidor: es donde se guarda, no que se pide.
  const { carpeta, ...peticion } = opciones;
  // `opciones` permite pedirlo por POST con un cuerpo: las credenciales se generan a partir
  // de una seleccion que puede ser de cientos de ids, y eso no cabe en una URL.
  const r = await apiFetch(ruta, peticion);
  if (!r.ok) {
    // El servidor explica en texto plano por que no hay documento (por ejemplo, que ninguna
    // de las credenciales pedidas cumple los requisitos). Decirlo es mas util que un generico.
    const motivo = await r.text().catch(() => '');
    throw new Error(motivo && motivo.length < 200 ? motivo : 'El servidor no devolvió el documento');
  }
  const blob = await r.blob();

  if (esNativo()) {
    return guardarEnElTelefono(blob, nombreArchivo, carpeta);
  }
  /*
   * En la web no hay carpeta que valga: el navegador decide donde cae y `download` ignora
   * cualquier ruta que se le ponga. Se antepone el nombre de la entidad al archivo, que
   * persigue lo mismo —que los de una misma venta queden juntos al ordenar por nombre— con lo
   * unico que el navegador deja controlar.
   */
  guardarEnElNavegador(blob, carpeta ? `${nombreSeguro(carpeta)} - ${nombreArchivo}` : nombreArchivo);
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

/**
 * Nombre de carpeta o de archivo que Android acepte.
 *
 * Los nombres de entidad traen barras ("EMPRESA / SERVICIO"), dos puntos y acentos, y una
 * barra dentro del nombre crearia una carpeta anidada donde no toca. Se quedan letras,
 * numeros, espacios, guiones y puntos; el resto pasa a guion y se recorta, porque Android no
 * garantiza nombres muy largos en todos los sistemas de archivos.
 */
export function nombreSeguro(v) {
  return String(v || '')
    .replace(/[^\p{L}\p{N} .\-_]/gu, '-')
    .replace(/-{2,}/g, '-')
    .replace(/\s{2,}/g, ' ')
    .trim()
    .slice(0, 60) || 'Sin nombre';
}

async function guardarEnElTelefono(blob, nombreArchivo, carpeta) {
  // El import es dinamico para que la web no cargue el plugin, que ahi no sirve de nada.
  const { Filesystem, Directory } = await import('@capacitor/filesystem');
  const datos = await aBase64(blob);
  /*
   * Una CARPETA POR VENTA, con el nombre de la entidad.
   *
   * Al registrar una venta caen varios archivos de golpe: el recibo y una credencial por cada
   * responsable. Sueltos en Documentos, mezclados con todo lo demas del telefono y con los de
   * las otras ventas del dia, encontrarlos era el trabajo. Juntos en una carpeta que se llama
   * como el expositor, se encuentran leyendo.
   *
   * `recursive: true` crea la carpeta si no existe; si ya existe, escribe dentro. Por eso una
   * segunda descarga de la misma venta cae junto a la primera en vez de duplicar nada.
   */
  const ruta = carpeta ? `${nombreSeguro(carpeta)}/${nombreArchivo}` : nombreArchivo;
  // Documents y no Cache: en Cache el archivo es invisible para el usuario y Android lo borra
  // cuando quiere. En Documentos queda donde lo va a buscar.
  const { uri } = await Filesystem.writeFile({
    path: ruta,
    data: datos,
    directory: Directory.Documents,
    recursive: true,
  });
  return { destino: 'telefono', uri, carpeta: carpeta ? nombreSeguro(carpeta) : null };
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
/**
 * Compartir el recibo por WhatsApp, correo o lo que el telefono tenga.
 *
 * Es lo que de verdad se hace con el papel: el cliente casi nunca quiere un PDF en la carpeta
 * Documentos del telefono del VENDEDOR, quiere el recibo en su WhatsApp. Bajarlo y luego
 * buscarlo en el gestor de archivos para adjuntarlo son cuatro pasos que sobran.
 *
 * En el APK se escribe primero en Cache —no en Documentos: aqui el archivo es un intermedio
 * para el envio, no algo que el vendedor vaya a buscar despues— y se comparte su ruta. En la
 * web se usa la API del navegador si acepta archivos; donde no (escritorio, sobre todo) se
 * cae a la descarga de siempre, que es un resultado util y no un error.
 */
export async function compartirRecibo(inscripcionId) {
  const nombre = `nota-venta-${inscripcionId}.pdf`;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${inscripcionId}/recibo`);
    if (!r.ok) throw new Error('El servidor no devolvió el recibo');
    const blob = await r.blob();

    if (esNativo()) {
      const { Filesystem, Directory } = await import('@capacitor/filesystem');
      const { Share } = await import('@capacitor/share');
      const { uri } = await Filesystem.writeFile({
        path: nombre, data: await aBase64(blob), directory: Directory.Cache, recursive: true,
      });
      await Share.share({ title: 'Recibo de la venta', url: uri });
      return true;
    }

    const archivo = new File([blob], nombre, { type: 'application/pdf' });
    if (navigator.canShare?.({ files: [archivo] })) {
      await navigator.share({ files: [archivo], title: 'Recibo de la venta' });
      return true;
    }
    // Sin compartir nativo, bajarlo es lo mas parecido a lo que pidio.
    guardarEnElNavegador(blob, nombre);
    toast('Tu navegador no puede compartir archivos: el recibo se descargó', 'info');
    return true;
  } catch (e) {
    // Cancelar el menu de compartir lanza AbortError. No es un fallo y no se avisa de nada.
    if (e?.name === 'AbortError') return false;
    toast(`No se pudo compartir el recibo: ${e.message}`, 'error');
    return false;
  }
}

export async function descargarRecibo(inscripcionId, carpeta = null) {
  try {
    const res = await descargarPdf(`/api/app/inscripciones/${inscripcionId}/recibo`,
                                   `nota-venta-${inscripcionId}.pdf`, { carpeta });
    toast(res.destino === 'telefono'
      ? (res.carpeta ? `Recibo guardado en Documentos › ${res.carpeta}` : 'Recibo guardado en Documentos')
      : 'Recibo descargado', 'ok');
    return true;
  } catch (e) {
    // La venta ya esta hecha: que falle la descarga no es motivo para alarmar, pero si para
    // decirle donde volver a pedirla.
    toast(`No se pudo descargar el recibo: ${e.message}. Puedes bajarlo desde Mis ventas.`, 'error');
    return false;
  }
}
