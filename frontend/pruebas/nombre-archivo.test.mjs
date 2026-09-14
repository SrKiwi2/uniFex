/**
 * El nombre de la carpeta donde caen los archivos de una venta.
 *
 * Se prueba aparte porque el nombre viene de un dato que escribe el vendedor —el nombre de la
 * entidad— y acaba siendo una RUTA en el telefono del usuario. Una barra dentro de ese nombre
 * crearia una carpeta anidada donde no toca, y hay entidades que se llaman literalmente
 * "EMPRESA / SERVICIO".
 *
 * Ejecutar:  npm --prefix frontend test
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { nombreSeguro } from '../src/ui/descargas.js';

test('una barra no crea carpetas anidadas', () => {
  assert.equal(nombreSeguro('EMPRESA / SERVICIO'), 'EMPRESA - SERVICIO');
  assert.ok(!nombreSeguro('A/B/C').includes('/'));
  assert.ok(!nombreSeguro('A\\B').includes('\\'));
});

test('deja pasar letras con acento y ñ, que abundan en los nombres reales', () => {
  assert.equal(nombreSeguro('ARTESANÍAS ÑANDUTÍ'), 'ARTESANÍAS ÑANDUTÍ');
});

test('quita lo que Android no admite en un nombre', () => {
  for (const malo of [':', '*', '?', '"', '<', '>', '|']) {
    assert.ok(!nombreSeguro(`A${malo}B`).includes(malo), malo);
  }
});

test('conserva guiones, puntos y numeros', () => {
  assert.equal(nombreSeguro('ASOC. 24 DE SEPTIEMBRE-2'), 'ASOC. 24 DE SEPTIEMBRE-2');
});

test('no deja el nombre vacio: siempre hay donde guardar', () => {
  for (const v of ['', '   ', null, undefined, '///']) {
    assert.ok(nombreSeguro(v).length > 0, JSON.stringify(v));
  }
});

test('recorta los muy largos, que no todos los sistemas de archivos aguantan', () => {
  assert.ok(nombreSeguro('X'.repeat(200)).length <= 60);
});

test('no deja rastros de la sustitucion en cadena', () => {
  // "A // B" no debe quedar como "A -- B".
  assert.equal(nombreSeguro('A // B'), 'A - B');
});
