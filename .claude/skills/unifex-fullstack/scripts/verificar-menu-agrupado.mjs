/**
 * El menu agrupado: que el cliente y el servidor digan lo MISMO.
 *
 * El menu de la SPA (`AppShell.TODOS`) y el catalogo del servidor
 * (`PantallasSistema`) llevan cada uno su propio campo de grupo. Tienen que coincidir: el
 * primero ordena la barra lateral y el segundo agrupa las casillas de "Permisos por rol", asi
 * que si discrepan la misma pantalla sale en un grupo distinto en cada sitio y no hay forma de
 * saber cual es el bueno. No falla al compilar ni da ningun error en pantalla.
 *
 * Tambien caza los dos olvidos tipicos al añadir una pantalla:
 *   - esta en el menu pero no en PantallasSistema (nadie puede concederla, es invisible);
 *   - esta en PantallasSistema pero no en el menu (existe y no hay como llegar).
 *
 * No necesita servidor ni base: lee los dos archivos.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-menu-agrupado.mjs
 */
import { readFileSync } from 'node:fs';

const RAIZ = '/home/usic-12/Documentos/RRHH KEVIN/SISTEMAS/uniFex/';
const leer = (p) => readFileSync(RAIZ + p, 'utf8');

let fallos = 0;
const ok = (c, m, extra = '') => {
  console.log(`${c ? '  ok  ' : ' FALLA'} ${m}${!c && extra ? ` — ${extra}` : ''}`);
  if (!c) fallos++;
};

console.log('\nMenu agrupado: cliente contra servidor\n');

// ---- servidor ----
const java = leer('src/main/java/com/usic/uniFex/security/PantallasSistema.java');
const servidor = new Map();
for (const m of java.matchAll(/^\s+[A-Z_]+\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\)/gm)) {
  servidor.set(m[1], { titulo: m[2], grupo: m[3] });
}
ok(servidor.size > 10, `PantallasSistema declara ${servidor.size} pantallas`);

// ---- cliente ----
const shell = leer('frontend/src/components/AppShell.vue');
const cliente = new Map();
for (const m of shell.matchAll(/\{\s*a:\s*'([^']+)',\s*p:\s*'([^']+)',\s*g:\s*'([^']+)'/g)) {
  cliente.set(m[2], { ruta: m[1], grupo: m[3] });
}
ok(cliente.size > 10, `el menu declara ${cliente.size} entradas con pantalla`);

// Los grupos que el menu sabe pintar. Uno que no este aqui no se veria: `grupos` recorre
// GRUPOS, no las entradas, asi que una entrada con un grupo inventado desaparece en silencio.
const gruposMenu = [...shell.matchAll(/\{\s*clave:\s*'([^']+)',\s*titulo:/g)].map((m) => m[1]);
ok(gruposMenu.length >= 3, `hay ${gruposMenu.length} grupos declarados`, gruposMenu.join(', '));

console.log('\nCoherencia');

// 1. Toda entrada del menu existe en el servidor.
const huerfanas = [...cliente.keys()].filter((p) => !servidor.has(p));
ok(huerfanas.length === 0,
  'toda entrada del menu corresponde a una pantalla del servidor',
  `sin declarar: ${huerfanas.join(', ')}`);

// 2. Toda pantalla del servidor tiene entrada en el menu.
const sinEnlace = [...servidor.keys()].filter((p) => !cliente.has(p));
ok(sinEnlace.length === 0,
  'toda pantalla del servidor tiene su entrada en el menu',
  `sin enlace: ${sinEnlace.join(', ')}`);

// 3. Y en el MISMO grupo.
const discrepan = [...cliente.entries()]
  .filter(([p, c]) => servidor.has(p) && servidor.get(p).grupo !== c.grupo)
  .map(([p, c]) => `${p}: menu="${c.grupo}" servidor="${servidor.get(p).grupo}"`);
ok(discrepan.length === 0, 'cada pantalla esta en el mismo grupo en los dos sitios',
  discrepan.join(' · '));

// 4. Ningun grupo inventado en las entradas.
const inventados = [...new Set([...cliente.values()].map((c) => c.grupo))]
  .filter((g) => !gruposMenu.includes(g));
ok(inventados.length === 0,
  'ninguna entrada usa un grupo que el menu no sabe pintar (desapareceria en silencio)',
  inventados.join(', '));

// 5. La barra inferior del movil sale de "Mi trabajo", no de los primeros cuatro sueltos.
ok(/enlacesPrincipales[\s\S]{0,400}?Mi trabajo/.test(shell),
  'la barra inferior del movil se toma del grupo "Mi trabajo"');

// 6. Cada ruta del menu existe en el router, o el enlace lleva a ninguna parte.
const router = leer('frontend/src/router.js');
const sinRuta = [...cliente.values()]
  .map((c) => c.ruta)
  .filter((r) => r !== '/' && !router.includes(`path: '${r.replace(/^\//, '')}'`));
ok(sinRuta.length === 0, 'cada enlace del menu tiene su ruta en el router', sinRuta.join(', '));

console.log(`\n${fallos === 0 ? 'Todo paso.' : `${fallos} comprobacion(es) fallidas.`}\n`);
process.exit(fallos === 0 ? 0 : 1);
