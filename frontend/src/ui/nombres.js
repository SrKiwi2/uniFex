/**
 * Partir un nombre completo en nombre de pila y apellidos.
 *
 * Hace falta porque los datos se piden de dos formas distintas y hay que pasar de una a otra:
 * la ENTIDAD guarda a su responsable legal como una sola cadena ("KEVIN CALLISAYA RIVERO"),
 * mientras que una PERSONA tiene nombre, paterno y materno en campos separados. Al marcar "es
 * el mismo responsable legal" hay que rellenar los segundos con el primero.
 *
 * <h2>La regla</h2>
 * En Bolivia se escriben dos apellidos, y van al final. Asi que **las dos ultimas palabras son
 * los apellidos** —la penultima el paterno, la ultima el materno— y todo lo anterior es el
 * nombre de pila. Eso resuelve solo el caso de los dos nombres: "MARIA LUISA QUISPE MAMANI"
 * da nombre "MARIA LUISA", y no hay que preguntar nada.
 *
 * Con menos de tres palabras no se puede adivinar, y se hace lo prudente en vez de inventar:
 * una sola palabra es el nombre, dos son nombre y un apellido. Nunca se rellena el materno a
 * base de suponer.
 *
 * <h2>Lo que NO hace</h2>
 * No intenta reconocer particulas ("DE LA CRUZ", "VAN DER BERG"). Se probo y la heuristica
 * fallaba en ambos sentidos; es mas honesto partir de forma predecible y dejar que el vendedor
 * corrija en pantalla, que es rapido, que acertar cuatro de cada cinco veces y que la quinta
 * salga impresa en una credencial. Por eso los campos NO se bloquean al marcar la casilla.
 */
export function partirNombre(completo) {
  const partes = String(completo || '').trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return { nombre: '', paterno: '', materno: '' };
  if (partes.length === 1) return { nombre: partes[0], paterno: '', materno: '' };
  if (partes.length === 2) return { nombre: partes[0], paterno: partes[1], materno: '' };
  return {
    nombre: partes.slice(0, -2).join(' '),
    paterno: partes[partes.length - 2],
    materno: partes[partes.length - 1],
  };
}
