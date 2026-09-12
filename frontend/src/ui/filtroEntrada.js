/**
 * Filtro de lo que se puede escribir en un campo: `v-filtro="'letras'"`, `v-filtro="'nombre'"`
 * o `v-filtro="'telefono'"`.
 *
 * Limpia mientras se escribe (y al pegar): lo no permitido ni siquiera aparece, y el cursor se
 * queda donde estaba. Funciona junto a v-model: tras limpiar vuelve a emitir `input`, así el
 * modelo recibe el valor ya limpio.
 *
 * Mientras hay una composición en curso (acentos con tecla muerta: ´ y luego a, o teclados de
 * móvil con predicción) NO se toca el valor: borrar la ´ a medias rompería la tilde. Se limpia
 * al terminar la composición.
 *
 * Es comodidad para quien escribe. La regla que vale es la del servidor
 * (InteresadoStandService), que valida lo mismo.
 */
// Sin dobles espacios ni espacio inicial; el espacio final se deja, que es el que separa la
// palabra siguiente mientras se escribe.
const espacios = (v) => v.replace(/ {2,}/g, ' ').replace(/^ /, '');

const FILTROS = {
  // Letras de cualquier alfabeto (tildes, ñ, ü) y espacios, en mayúsculas.
  letras: (v) => espacios(v.toLocaleUpperCase('es-BO').replace(/[^\p{L} ]/gu, '')),
  // Nombre de persona: letras, puntos y signos (J. PÉREZ, O'HIGGINS, ANA-MARÍA), en
  // mayúsculas. Lo único que no entra son números (y caracteres de control).
  nombre: (v) => espacios(v.toLocaleUpperCase('es-BO').replace(/[\p{N}\p{Cc}]/gu, '')),
  // Dígitos y un "+" de prefijo de país. El "+" se acepta donde se escriba y se coloca solo al
  // inicio: quien ya tecleó el número y luego añade el prefijo lo escribe al final o en medio,
  // y antes ese "+" se borraba sin explicación ("no me deja poner el +").
  telefono: (v) => (v.includes('+') ? '+' : '') + v.replace(/\D/g, ''),
};

/** Celular válido: "+" opcional y de 7 a 15 dígitos (lo mismo que exige el servidor). */
export const CELULAR_VALIDO = /^\+?\d{7,15}$/;

export const vFiltro = {
  mounted(el, binding) {
    const limpiar = FILTROS[binding.value];
    if (!limpiar) return;
    const aplicar = (e) => {
      if (e?.isComposing) return;
      const antes = el.value;
      const despues = limpiar(antes);
      if (despues === antes) return;
      // El cursor va donde termina la parte limpia de lo que había antes de él.
      const pos = el.selectionStart ?? antes.length;
      const nuevaPos = limpiar(antes.slice(0, pos)).length;
      el.value = despues;
      try { el.setSelectionRange(nuevaPos, nuevaPos); } catch { /* tipos sin selección */ }
      el.dispatchEvent(new Event('input', { bubbles: true }));
    };
    el._filtroEntrada = aplicar;
    el.addEventListener('input', aplicar);
    el.addEventListener('compositionend', aplicar);
  },
  unmounted(el) {
    el.removeEventListener('input', el._filtroEntrada);
    el.removeEventListener('compositionend', el._filtroEntrada);
  },
};
