/**
 * Partir un nombre completo en nombre y apellidos.
 *
 * Se prueba aparte porque es una regla de datos, no de pantalla: lo que salga de aqui acaba
 * IMPRESO en la credencial que el expositor enseña en la puerta. El fallo que lo motivo metia
 * el nombre completo entero en la casilla "nombre" y dejaba los apellidos vacios.
 *
 * Ejecutar:  npm --prefix frontend test
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { partirNombre } from '../src/ui/nombres.js';

test('tres palabras: nombre, paterno y materno', () => {
  assert.deepEqual(partirNombre('KEVIN CALLISAYA RIVERO'),
    { nombre: 'KEVIN', paterno: 'CALLISAYA', materno: 'RIVERO' });
});

test('dos nombres de pila: los apellidos siguen siendo los dos ultimos', () => {
  assert.deepEqual(partirNombre('MARIA LUISA QUISPE MAMANI'),
    { nombre: 'MARIA LUISA', paterno: 'QUISPE', materno: 'MAMANI' });
});

test('tres nombres de pila tambien', () => {
  assert.deepEqual(partirNombre('JUAN CARLOS ALBERTO PEREZ SOTO'),
    { nombre: 'JUAN CARLOS ALBERTO', paterno: 'PEREZ', materno: 'SOTO' });
});

test('dos palabras: nombre y un solo apellido, sin inventarse el materno', () => {
  assert.deepEqual(partirNombre('ANA QUISPE'),
    { nombre: 'ANA', paterno: 'QUISPE', materno: '' });
});

test('una sola palabra: es el nombre', () => {
  assert.deepEqual(partirNombre('MADONNA'),
    { nombre: 'MADONNA', paterno: '', materno: '' });
});

test('espacios de sobra y saltos no cuentan como palabras', () => {
  assert.deepEqual(partirNombre('  KEVIN   CALLISAYA  RIVERO '),
    { nombre: 'KEVIN', paterno: 'CALLISAYA', materno: 'RIVERO' });
});

test('vacio, nulo o indefinido no revientan', () => {
  for (const v of ['', '   ', null, undefined]) {
    assert.deepEqual(partirNombre(v), { nombre: '', paterno: '', materno: '' });
  }
});
