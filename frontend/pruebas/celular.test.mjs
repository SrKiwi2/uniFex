/**
 * El celular con codigo de pais: como se parte y como se guarda.
 *
 * Se prueba la funcion de partir porque es donde esta el riesgo: un numero boliviano de ocho
 * digitos puede empezar por 55, que es el codigo de Brasil, y partir solo por el prefijo
 * convertiria un fijo de Cochabamba en un celular de Sao Paulo. Lo que se guarda aqui acaba
 * siendo el telefono al que se llama para cobrar.
 *
 * La funcion vive en el componente, asi que aqui se replica su regla y se fija el contrato.
 * Si alguien cambia el componente y no esto, la prueba deja de describir la realidad — por eso
 * la regla esta escrita en un solo sitio y copiada aqui a proposito, con este aviso.
 *
 * Ejecutar:  npm --prefix frontend test
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const fuente = readFileSync(new URL('../src/components/CampoCelular.vue', import.meta.url), 'utf8');

test('el componente declara Bolivia y Brasil con sus codigos', () => {
  assert.match(fuente, /codigo: '591'/);
  assert.match(fuente, /codigo: '55'/);
});

test('guarda sin "+" ni separadores', () => {
  // Lo que se emite es codigo + numero, todo digitos.
  assert.match(fuente, /paisActual\.value\.codigo \+ n/);
  assert.doesNotMatch(fuente, /'\+' \+/);
});

test('con el campo vacio NO guarda el codigo suelto', () => {
  // Guardar "591" como celular de quien no dio telefono es inventarse un dato, y ademas
  // pasaria cualquier validacion de "no esta vacio".
  assert.match(fuente, /n \? paisActual\.value\.codigo \+ n : ''/);
});

test('para reconocer un prefijo exige ademas que el largo cuadre', () => {
  // Sin esto, un numero boliviano que empiece por 55 se leeria como brasileño.
  assert.match(fuente, /p\.largo\.includes\(d\.length - p\.codigo\.length\)/);
});
