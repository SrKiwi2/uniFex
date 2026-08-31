import { ref } from 'vue';

/**
 * Tema claro/oscuro. Sin preferencia guardada, manda el sistema (prefers-color-scheme);
 * al elegir uno, se estampa data-theme en <html> y se recuerda en localStorage.
 */
const GUARDADO = localStorage.getItem('tema'); // 'light' | 'dark' | null
export const tema = ref(GUARDADO || 'auto');

function aplicar() {
  const raiz = document.documentElement;
  if (tema.value === 'auto') raiz.removeAttribute('data-theme');
  else raiz.setAttribute('data-theme', tema.value);
}

export function alternarTema() {
  // auto -> dark -> light -> auto
  tema.value = tema.value === 'auto' ? 'dark' : tema.value === 'dark' ? 'light' : 'auto';
  if (tema.value === 'auto') localStorage.removeItem('tema');
  else localStorage.setItem('tema', tema.value);
  aplicar();
}

export function iniciarTema() {
  aplicar();
}
