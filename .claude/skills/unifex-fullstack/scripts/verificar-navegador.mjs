#!/usr/bin/env node
/**
 * Recorrido de la aplicacion en un NAVEGADOR DE VERDAD (Chrome sin ventana).
 *
 * Existe por un fallo concreto: en Login.vue, un `ref="contrasena"` de la plantilla y una
 * variable `const contrasena = ref('')` compartian nombre. En <script setup>, la plantilla
 * ESCRIBE en la variable del script, asi que al montarse la vista Vue metia el propio <input>
 * dentro de `contrasena`; el boton llamaba a `contrasena.trim()`, el render reventaba y la
 * pantalla de login quedaba EN BLANCO. Nadie podia entrar.
 *
 * Ninguna prueba de API lo habria visto: el servidor estaba perfecto. Hace falta montar la
 * aplicacion de verdad, y eso es lo que hace esto.
 *
 * Requisitos: `google-chrome` instalado y la app sirviendose en alguna parte.
 *
 *   npm --prefix frontend run dev        # en otra terminal, con el backend en 7676
 *   node .claude/skills/unifex-fullstack/scripts/verificar-navegador.mjs
 *
 * Opciones por entorno: URL (por defecto http://localhost:5173), USUARIO, CLAVE.
 */
import { abrirChrome, erroresDe } from './lib-navegador.mjs';

const URL = process.env.URL || 'http://localhost:5173';
const USUARIO = process.env.USUARIO || 'admin1';
const CLAVE = process.env.CLAVE || 'VO7xGroB8ag2Qz1B';

const fallos = [];
const paso = (d, ok, det = '') => {
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${d}${det ? `  -> ${det}` : ''}`);
  if (!ok) fallos.push(d);
  return ok;
};
const titulo = (t) => console.log(`\n${t}`);

try {
  await fetch(URL, { method: 'GET' });
} catch {
  console.log(`No hay nada sirviendo en ${URL}. Levanta el front y vuelve a intentarlo.`);
  process.exit(2);
}

const ch = await abrirChrome();
try {
  // Teclear como una persona: el setter nativo mas un evento `input`, que es a lo que
  // reacciona v-model. Asignar `el.value` a secas no lo despierta.
  const teclear = (sel, texto) => ch.evaluar(`(() => {
    const el = document.querySelector(${JSON.stringify(sel)});
    if (!el) return null;
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ${JSON.stringify(texto)});
    el.dispatchEvent(new Event('input', { bubbles: true }));
    return el.value;
  })()`);

  titulo('Login');
  await ch.ir(URL + '/login');
  await ch.evaluar('localStorage.clear(); sessionStorage.clear();');
  await ch.ir(URL + '/login');
  await ch.esperar(2600);   // la bienvenida dura ~900 ms mas su desvanecido

  paso('la vista de login se monta', await ch.evaluar('!!document.querySelector("#usuario")'));
  paso('la bienvenida ya se fue', await ch.evaluar('!document.querySelector(".bienvenida")'));
  // El sintoma exacto del fallo que motivo este script.
  paso('el campo de contraseña arranca vacio',
       (await ch.evaluar('document.querySelector("#contrasena")?.value')) === '');

  paso('acepta el usuario', (await teclear('#usuario', USUARIO)) === USUARIO);
  paso('acepta la contraseña', (await teclear('#contrasena', CLAVE)) === CLAVE);
  await ch.esperar(150);
  paso('el boton Ingresar se habilita',
       await ch.evaluar('!document.querySelector(".btn-entrar").disabled'));

  await ch.evaluar('document.querySelector(".btn-entrar").click()');
  await ch.esperar(2500);
  paso('entrega un token', !!(await ch.evaluar('localStorage.getItem("token")')));
  paso('y sale de la pantalla de login',
       !(await ch.evaluar('location.pathname')).startsWith('/login'),
       await ch.evaluar('location.pathname'));

  titulo('Bienvenida y vistas');
  await ch.ir(URL + '/');
  await ch.esperar(250);
  paso('la bienvenida sale en cada recarga, ya con sesion',
       await ch.evaluar('!!document.querySelector(".bienvenida")'));
  await ch.esperar(2400);
  paso('y se quita sola', await ch.evaluar('!document.querySelector(".bienvenida")'));

  await ch.ir(URL + '/mapa');
  await ch.esperar(4000);
  const casetas = await ch.evaluar('document.querySelectorAll(".pin").length');
  paso('el mapa pinta casetas', casetas > 0, `${casetas} pines`);
  paso('el plano carga', await ch.evaluar('!!document.querySelector(".plano img")'));
  const desfase = await ch.evaluar(
    'document.querySelector(".aviso-sync")?.textContent.trim() || ""');
  paso('el tiempo real conecta (sin aviso de desfase)', !desfase, desfase);

  await ch.ir(URL + '/venta');
  await ch.esperar(2600);
  paso('el formulario de venta abre', await ch.evaluar('!!document.querySelector(".venta")'));

  titulo('Consola');
  const errores = erroresDe(ch.eventos)
    .filter((t) => !/favicon|DevTools|Download the Vue/i.test(t));
  paso('ni un error de JavaScript en todo el recorrido', errores.length === 0,
       errores.slice(0, 3).join(' | '));
} finally {
  ch.cerrar();
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
