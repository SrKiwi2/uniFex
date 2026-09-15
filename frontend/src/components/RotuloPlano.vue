<script setup>
/**
 * Un rotulo del plano, dibujado dentro del mundo que transforma PanZoom.
 *
 * Lo comparten el Mapa (solo mirar) y el Editor (colocar y mover) para que el rotulo se vea
 * EXACTAMENTE igual en los dos: si cada vista lo pintara por su cuenta, el administrador
 * colocaria el texto en un sitio y los vendedores lo verian en otro tamaño o posicion, que es
 * la peor forma de equivocarse en un plano.
 *
 * Todo se mide contra `--mundo`, el ancho de diseño del plano que publica PanZoom, y NO en
 * pixeles: asi el rotulo escala con el zoom igual que las casetas. Un `px` aqui dentro no es un
 * pixel de pantalla.
 */
const props = defineProps({
  texto: { type: Object, required: true },
  /** Resalta el rotulo cuando el Editor lo tiene seleccionado. */
  seleccionado: { type: Boolean, default: false },
});

/**
 * El contorno se hace con cuatro sombras y no con `-webkit-text-stroke`.
 *
 * `text-stroke` dibuja el borde CENTRADO sobre el trazo de la letra, asi que se come el relleno
 * y con grosores altos el texto se vuelve ilegible. Cuatro sombras en diagonal dan un contorno
 * por fuera, que es lo que hace legible un rotulo sobre una imagen de cualquier color.
 */
const contorno = (color, g) => {
  const d = `${g}em`;
  return [`${d} ${d}`, `-${d} ${d}`, `${d} -${d}`, `-${d} -${d}`]
    .map((o) => `${o} 0 ${color}`).join(', ');
};
</script>

<template>
  <div
    class="rotulo"
    :class="{ seleccionado }"
    :style="{
      left: `calc(${(texto.mapaX ?? 0.5) * 100}% )`,
      top: `calc(${(texto.mapaY ?? 0.5) * 100}% )`,
      fontSize: `calc(var(--mundo) * ${texto.tamano ?? 0.02})`,
      color: texto.color || '#111827',
      textShadow: contorno(texto.colorBorde || '#ffffff', texto.grosorBorde ?? 0.15),
      transform: `translate(-50%, -50%) rotate(${texto.rotacion ?? 0}deg)`,
    }"
  >{{ texto.contenido }}</div>
</template>

<style scoped>
.rotulo {
  position: absolute;
  /* Por defecto NO se puede tocar: en el Mapa es decoracion y un rotulo que robe el toque
     impediria seleccionar la caseta que tiene debajo. El Editor lo reactiva con una clase. */
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.02em;
}
.rotulo.seleccionado {
  outline: 2px dashed var(--acento);
  outline-offset: 0.15em;
}
</style>
