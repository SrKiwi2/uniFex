import { escucharListasPublicas, SUSCRIPCION_NOCHES } from './publicoEnVivo.js';

/**
 * Cartelera de "Noches de FEXPO" en vivo (ver NochesFexpoEventPublisher). Atajo de
 * escucharListasPublicas para quien solo necesita las noches, como el panel de admin.
 *
 * @param onLista recibe la lista de noches (NocheFexpoDTO[]) cada vez que cambia
 * @returns funcion para cerrar la conexion (llamarla al desmontar la vista)
 */
export function escucharNoches(onLista) {
  return escucharListasPublicas([{ ...SUSCRIPCION_NOCHES, onLista }]);
}
