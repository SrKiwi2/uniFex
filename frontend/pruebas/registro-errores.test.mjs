import test from 'node:test';
import assert from 'node:assert/strict';

let token = 'sesion-prueba';
globalThis.localStorage = { getItem: () => token };
globalThis.window = { location: { pathname: '/mis-ventas' } };
const { registrarError, enviarPendientes } = await import('../src/ui/registroErrores.js');

const esperar = () => new Promise(resolve => setImmediate(resolve));

test('registra, oculta secretos y agrupa errores repetidos sin modificar el error original', async () => {
  const enviados = [];
  globalThis.fetch = async (ruta, opciones) => { enviados.push({ ruta, opciones }); return { status: 204 }; };
  const error = new Error('Fallo password=privado token=secreto');
  registrarError(error);
  registrarError(error);
  await esperar();
  assert.equal(enviados.length, 1);
  assert.equal(enviados[0].ruta, '/api/app/errores/cliente');
  const cuerpo = JSON.parse(enviados[0].opciones.body);
  assert.equal(cuerpo.ruta, '/mis-ventas');
  assert.ok(!enviados[0].opciones.body.includes('privado'));
  assert.ok(!enviados[0].opciones.body.includes('secreto'));
  assert.ok(error.message.includes('privado'));
});

test('un fallo del propio registro no genera otro error y se reintenta al recuperar red', async () => {
  globalThis.fetch = async () => { throw new Error('sin red'); };
  assert.doesNotThrow(() => registrarError('Error sin conexion'));
  await esperar();
  const enviados = [];
  globalThis.fetch = async (_, opciones) => { enviados.push(JSON.parse(opciones.body)); return { status: 204 }; };
  await enviarPendientes();
  assert.equal(enviados.length, 1);
  assert.equal(enviados[0].mensaje, 'Error sin conexion');
});

test('descarta errores pendientes cuando cambia el usuario y no envia sin sesion', async () => {
  globalThis.fetch = async () => { throw new Error('sin red'); };
  registrarError('Error de la sesion anterior');
  await esperar();
  token = 'otra-sesion';
  let enviados = 0;
  globalThis.fetch = async () => { enviados++; return { status: 204 }; };
  await enviarPendientes();
  assert.equal(enviados, 0);
  token = '';
  registrarError('Sin usuario');
  await esperar();
  assert.equal(enviados, 0);
});
