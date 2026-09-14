<script setup>
import { ref, computed, watch } from 'vue';

/*
 * Un celular con su codigo de pais.
 *
 * La feria recibe expositores de Bolivia y de Brasil, y hasta ahora el celular se guardaba
 * como se tecleara: ocho digitos sueltos. Eso basta para llamar desde el pais, pero no dice de
 * donde es el numero, y un numero brasileño guardado asi no se puede marcar.
 *
 * <h2>Como se guarda</h2>
 * Como UNA sola cadena de digitos con el codigo delante y sin simbolos: "59174754979". Sin el
 * "+", sin espacios y sin guiones. Es lo que pidio el cliente y ademas es lo que menos duele:
 * el valor se puede comparar, buscar y exportar sin normalizar nada, y quien quiera mostrarlo
 * bonito lo formatea al pintarlo.
 *
 * <h2>Por que la division no es "cortar por el prefijo"</h2>
 * Un numero boliviano de ocho digitos puede empezar por 55, que es justo el codigo de Brasil.
 * Si se partiera solo por el prefijo, un fijo de Cochabamba se leeria como un celular de Sao
 * Paulo. Por eso se exige ADEMAS que el largo total cuadre con ese pais. Con los numeros que
 * ya hay en la base —de seis a nueve digitos, sin codigo— ninguno cuadra, asi que se muestran
 * tal cual y se les asigna el pais por defecto, que es lo correcto: son de antes de esto.
 */

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** Se pasa al input de abajo: el aviso de campo obligatorio se pinta ahi. */
  falta: { type: Boolean, default: false },
  id: { type: String, default: null },
});
const emit = defineEmits(['update:modelValue']);

const PAISES = [
  { codigo: '591', pais: 'BO', bandera: '🇧🇴', nombre: 'Bolivia', largo: [8], ejemplo: '71234567' },
  { codigo: '55', pais: 'BR', bandera: '🇧🇷', nombre: 'Brasil', largo: [10, 11], ejemplo: '11987654321' },
];
const POR_DEFECTO = PAISES[0];

const soloDigitos = (v) => String(v || '').replace(/\D/g, '');

/** Parte un valor guardado en pais + numero nacional. Ver la nota de arriba sobre el largo. */
function partir(valor) {
  const d = soloDigitos(valor);
  for (const p of PAISES) {
    if (d.startsWith(p.codigo) && p.largo.includes(d.length - p.codigo.length)) {
      return { pais: p.pais, numero: d.slice(p.codigo.length) };
    }
  }
  return { pais: POR_DEFECTO.pais, numero: d };
}

const inicial = partir(props.modelValue);
const pais = ref(inicial.pais);
const numero = ref(inicial.numero);

const paisActual = computed(() => PAISES.find((p) => p.pais === pais.value) || POR_DEFECTO);

/*
 * Se avisa hacia fuera solo cuando hay numero. Con el campo vacio se emite cadena vacia y NO
 * el codigo suelto: guardar "591" como celular de alguien que no dio telefono es inventarse un
 * dato, y ademas pasaria cualquier validacion de "no esta vacio".
 */
function avisar() {
  const n = soloDigitos(numero.value);
  emit('update:modelValue', n ? paisActual.value.codigo + n : '');
}

function alEscribir(e) {
  numero.value = soloDigitos(e.target.value);
  avisar();
}

watch(pais, avisar);

// Si el valor cambia desde fuera —un borrador recuperado, o la ficha que se recarga— hay que
// volver a partirlo. Se ignora cuando coincide con lo que acabamos de emitir, para no pelear
// con lo que el usuario esta tecleando.
watch(() => props.modelValue, (v) => {
  const actual = soloDigitos(numero.value)
    ? paisActual.value.codigo + soloDigitos(numero.value) : '';
  if (soloDigitos(v) === soloDigitos(actual)) return;
  const p = partir(v);
  pais.value = p.pais;
  numero.value = p.numero;
});
</script>

<template>
  <div class="celular" :class="{ falta }">
    <!-- El select lleva la bandera y el codigo. La bandera sola no basta: a tamaño de texto
         dos banderas se distinguen mal, y el codigo es lo que la gente reconoce. -->
    <select v-model="pais" class="pais" :aria-label="`País: ${paisActual.nombre}`">
      <option v-for="p in PAISES" :key="p.pais" :value="p.pais">
        {{ p.bandera }} +{{ p.codigo }}
      </option>
    </select>
    <input
      :id="id"
      class="control numero"
      type="tel"
      inputmode="numeric"
      autocomplete="tel-national"
      :value="numero"
      :placeholder="paisActual.ejemplo"
      @input="alEscribir"
    />
  </div>
</template>

<style scoped>
/* Los dos como una sola pieza: el codigo y el numero son un dato, no dos campos. */
.celular { display: flex; align-items: stretch; gap: 0; }
.pais {
  flex: none; font: inherit; cursor: pointer;
  border: 1px solid var(--border); border-right: none;
  border-radius: var(--radio-sm) 0 0 var(--radio-sm);
  background: var(--panel-2); color: var(--texto);
  padding: 0 0.5rem; min-height: 44px;
}
.numero { border-radius: 0 var(--radio-sm) var(--radio-sm) 0; flex: 1; min-width: 0; }
.celular:focus-within .pais { border-color: var(--acento); }
/* El aviso de obligatorio rodea la pieza entera: marcar solo el numero dejaria el selector
   con su borde normal y la fila se veria partida. */
.celular.falta .pais,
.celular.falta .numero { border-color: var(--danger); border-width: 2px; }
.celular.falta .pais { border-right: none; }
.celular.falta .numero { background: var(--danger-suave); }
</style>
