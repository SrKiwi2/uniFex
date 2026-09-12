/**
 * Conductor minimo de Chrome por el protocolo DevTools. Sin dependencias: Node ya trae
 * WebSocket y fetch. Se usa para probar el login de verdad, en un navegador, que es donde
 * fallaba — un fallo de render no se ve desde una peticion HTTP.
 */
import { spawn } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export async function abrirChrome(puerto = 9222) {
  const perfil = mkdtempSync(join(tmpdir(), 'chrome-prueba-'));
  const proc = spawn('google-chrome', [
    '--headless=new', `--remote-debugging-port=${puerto}`, '--no-sandbox', '--disable-gpu',
    '--disable-dev-shm-usage', '--no-first-run', '--no-default-browser-check',
    `--user-data-dir=${perfil}`, 'about:blank',
  ], { stdio: 'ignore' });

  let destino = null;
  for (let i = 0; i < 60 && !destino; i++) {
    await new Promise((r) => setTimeout(r, 250));
    try {
      const lista = await (await fetch(`http://127.0.0.1:${puerto}/json/list`)).json();
      destino = lista.find((t) => t.type === 'page');
    } catch { /* aun no levanta */ }
  }
  if (!destino) { proc.kill(); throw new Error('Chrome no levanto'); }

  const ws = new WebSocket(destino.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });

  let n = 0;
  const pendientes = new Map();
  const eventos = [];
  ws.onmessage = (m) => {
    const d = JSON.parse(m.data);
    if (d.id && pendientes.has(d.id)) {
      const { res, rej } = pendientes.get(d.id);
      pendientes.delete(d.id);
      d.error ? rej(new Error(d.error.message)) : res(d.result);
    } else if (d.method) {
      eventos.push(d);
    }
  };
  const enviar = (method, params = {}) => new Promise((res, rej) => {
    const id = ++n;
    pendientes.set(id, { res, rej });
    ws.send(JSON.stringify({ id, method, params }));
  });

  await enviar('Runtime.enable');
  await enviar('Page.enable');
  await enviar('Log.enable');

  const evaluar = async (expr) => {
    const r = await enviar('Runtime.evaluate', {
      expression: expr, awaitPromise: true, returnByValue: true,
    });
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || 'error en la pagina');
    return r.result.value;
  };

  return {
    enviar,
    evaluar,
    eventos,
    ir: async (url) => {
      await enviar('Page.navigate', { url });
      await new Promise((r) => setTimeout(r, 400));
    },
    esperar: (ms) => new Promise((r) => setTimeout(r, ms)),
    cerrar: () => { try { ws.close(); } catch {} proc.kill(); },
  };
}

/** Errores que la pagina escupio por consola o excepciones sin capturar. */
export function erroresDe(eventos) {
  const fuera = [];
  for (const e of eventos) {
    if (e.method === 'Runtime.exceptionThrown') {
      fuera.push(e.params.exceptionDetails?.exception?.description
        || e.params.exceptionDetails?.text || 'excepcion');
    }
    if (e.method === 'Runtime.consoleAPICalled' && e.params.type === 'error') {
      fuera.push(e.params.args.map((a) => a.description || a.value).join(' '));
    }
    if (e.method === 'Log.entryAdded' && e.params.entry.level === 'error') {
      fuera.push(e.params.entry.text);
    }
  }
  return fuera;
}
