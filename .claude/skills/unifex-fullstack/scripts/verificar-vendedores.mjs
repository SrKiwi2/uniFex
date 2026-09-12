/**
 * Humo del modulo de VENDEDORES (observacion 10): que cada vendedor vea y venda SOLO lo suyo.
 *
 * Cubre los fallos que tenia la primera version y que no daba ninguna prueba:
 *   - la consulta del mapa reventaba con 42P10 (SELECT DISTINCT + ORDER BY c.nombre),
 *     asi que NINGUN vendedor podia abrir el mapa;
 *   - asignar una caseta por numero no servia de nada si el vendedor no tenia ademas su
 *     categoria (el LEFT JOIN colgaba de vc.id_usuario);
 *   - GET /vendedores/{id}/puestos devolvia una lista vacia fija;
 *   - GET /vendedores/{id}/categorias reventaba (JPQL contra una tabla sin entidad);
 *   - esconder una caseta en el mapa NO impedia venderla: ni el carrito ni el registro
 *     de la venta comprobaban la asignacion.
 *
 * Regla: un vendedor ve EXACTAMENTE las casetas que se le seleccionaron, ni una mas.
 * Sin seleccion no ve ninguna. Nadie vende hasta que
 * administracion le habilita una categoria o unas casetas, y el mapa se lo dice con
 * palabras en vez de dejarlo mirando un plano vacio.
 *
 * OJO al desplegar: los vendedores que ya existan se quedan sin mapa hasta que se les
 * asigne algo. Hay que configurarlos antes de subirlo.
 *
 * Deja la base como estaba: borra el vendedor de prueba al terminar.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-vendedores.mjs
 *   opciones: --base http://localhost:7676
 */
const B = process.argv.includes('--base') ? process.argv[process.argv.indexOf('--base')+1] : 'http://localhost:8099';
const marca = Date.now().toString().slice(-6);
let fallos = 0;
const ok = (c, m, extra='') => { console.log(`${c?'  OK  ':' FALLA'} ${m}${extra?' :: '+extra:''}`); if(!c) fallos++; };
const j = async r => { try { return await r.json(); } catch { return null; } };
const login = async (u,c) => { const r = await fetch(`${B}/api/auth/login`,{method:'POST',
  headers:{'Content-Type':'application/json'},body:JSON.stringify({usuario:u,contrasena:c})});
  return { estado:r.status, ...(await j(r)||{}) }; };
const api = async (t,ruta,o={}) => { const r = await fetch(B+ruta,{...o,
  headers:{Authorization:`Bearer ${t}`,...(o.body?{'Content-Type':'application/json'}:{})}});
  return { estado:r.status, cuerpo: await j(r) }; };

console.log(`\n== Modulo de vendedores en ${B} ==\n`);
const T = (await login('admin1','VO7xGroB8ag2Qz1B')).token;
const todas = (await api(T,'/api/app/puestos')).cuerpo;
const rolAdm = (await api(T,'/api/app/roles')).cuerpo.find(r=>r.nombre==='ADMINISTRATIVO');

/*
 * Barrido de arranque. Estos guiones crean un vendedor con nombre irrepetible y lo borran al
 * final, pero si uno se corta a la mitad ese usuario se queda VIVO, con una clave que esta
 * escrita en este mismo archivo. Ejecutandose contra produccion eso es un vendedor de verdad
 * al que puede entrar cualquiera que lea el repositorio. Asi que antes de empezar se limpia lo
 * que dejaron las pasadas anteriores.
 */
for (const u of ((await api(T, '/api/app/usuarios')).cuerpo || [])) {
  if (/^vend\d+$/.test(u.username || '')) {
    await api(T, `/api/app/usuarios/${u.id}`, { method: 'DELETE' });
  }
}

const usuario = `vend${marca}`;
const vid = (await api(T,'/api/app/usuarios',{method:'POST',body:JSON.stringify({
  username:usuario,password:'ClaveVendedor9',rolId:rolAdm.id,personaId:null,
  persona:{nombre:'VENDEDOR',paterno:'PRUEBA',materno:'',ci:`VP${marca}`,correo:'',celular:''}})})).cuerpo?.usuario?.id;
const V = (await login(usuario,'ClaveVendedor9')).token;
ok(!!vid && !!V, 'se crea un vendedor de prueba y entra', usuario);

// A) Habilitada != visible. El vendedor ve TODO el plano desde el primer dia; lo que cambia
// con las habilitaciones es cuales puede vender. Esta prueba llego a exigir lo contrario
// —cero casetas sin habilitaciones— de cuando el servidor filtraba el listado; desde que se
// decidio ensenarle las ajenas en gris, esa expectativa era la que estaba mal.
let r = await api(V,'/api/app/puestos');
ok(r.estado === 200, 'un vendedor puede abrir el mapa (antes era HTTP 500)', `HTTP ${r.estado}`);
ok(Array.isArray(r.cuerpo) && r.cuerpo.length === todas.length,
   've el plano entero, tenga o no casetas habilitadas', `${r.cuerpo?.length} de ${todas.length}`);
r = await api(V,'/api/app/mis-puestos');
ok(Array.isArray(r.cuerpo) && r.cuerpo.length === 0,
   'pero todavia no tiene ninguna suya', `${r.cuerpo?.length} suya(s)`);

// B) Una caseta suelta, SIN su categoria: el caso que antes no funcionaba nunca
const catalogo = (await api(T,'/api/app/vendedores/puestos-asignables')).cuerpo || [];
const libres = new Set(catalogo.filter(p => !p.asignadoAId).map(p => p.id));
const obj = todas.find(p => p.estado === 'L' && libres.has(p.id));
ok(!!obj, 'hay una caseta libre y sin dueño para la prueba', obj?.codigo);
// PUT, no POST: la seleccion se manda entera y el servidor calcula altas y bajas. Con POST
// esto daba 405, que la cadena 2 convierte en un 302 al login — y fetch moria con
// "redirect count exceeded", sin que se viera por ningun lado que la ruta estaba mal.
r = await api(T,`/api/app/vendedores/${vid}/puestos`,{method:'PUT',body:JSON.stringify({puestoIds:[obj.id]})});
ok(r.estado === 200 && r.cuerpo?.asignadas === 1, 'se le habilita UNA caseta suelta', `HTTP ${r.estado}`);
r = await api(V,'/api/app/mis-puestos');
ok(Array.isArray(r.cuerpo) && r.cuerpo.length === 1 && r.cuerpo[0].id === obj.id,
   'con UNA caseta habilitada, esa es la unica suya', `${r.cuerpo?.length}: ${r.cuerpo?.[0]?.codigo}`);
r = await api(V,'/api/app/puestos');
ok(r.cuerpo?.length === todas.length, 'y el plano lo sigue viendo entero', `${r.cuerpo?.length}`);

// Lo que hace util ver las ajenas: saber a quien derivar al cliente que esta parado delante.
r = await api(V,'/api/app/puestos/asignaciones');
const mia = (r.cuerpo || []).find(a => a.puestoId === obj.id);
ok(!!mia && !!mia.vendedor, 'las asignaciones dicen quien lleva cada caseta', mia?.vendedor);

r = await api(T,`/api/app/vendedores/${vid}/puestos`);
ok(Array.isArray(r.cuerpo) && r.cuerpo.length === 1,
   'GET /vendedores/{id}/puestos devuelve lo asignado (antes: lista vacia fija)', `${r.cuerpo?.length} caseta(s)`);


// C) No puede reservar una caseta ajena
const ajena = todas.find(p => p.id !== obj.id && p.estado === 'L' && libres.has(p.id));
r = await api(V,'/api/app/puestos/carrito',{method:'POST',body:JSON.stringify({ids:[ajena.id]})});
ok(r.estado === 403 && (r.cuerpo?.logradas?.length ?? 0) === 0,
   'NO puede reservar una caseta que no tiene asignada', `HTTP ${r.estado}`);

// D) Si puede reservar la suya
r = await api(V,'/api/app/puestos/carrito',{method:'POST',body:JSON.stringify({ids:[obj.id]})});
ok(r.estado === 200 && r.cuerpo?.logradas?.length === 1, 'SI puede reservar la suya', `logradas=${r.cuerpo?.logradas?.length}`);
await api(V,'/api/app/puestos/carrito',{method:'DELETE',body:JSON.stringify({ids:[obj.id]})});

// E) Vender por el endpoint de inscripciones, saltandose el carrito
const tipos = (await api(T,'/api/app/catalogos/tipos-entidad')).cuerpo;
r = await api(V,'/api/app/inscripciones',{method:'POST',body:JSON.stringify({
  entidadNombre:`INTENTO ${marca}`, nit:'1', descripcion:'d', objeto:'o',
  representanteLegal:'R', ciRepresentante:'1', celularRepresentante:'70000000',
  tipoEntidadId:tipos?.[0]?.id, fechaInicio:null, fechaFin:null,
  responsables:[{nombre:'A',paterno:'B',materno:'',ci:`R${marca}`,celular:null}],
  entidadBancaria:'B', numComprobante:1, pagoContado:true, puestos:[ajena.id]})});
ok(r.estado === 403, 'NO puede vender una caseta ajena saltandose el carrito', `HTTP ${r.estado}`);
// Si el control fallara, la venta SI se crea. Se deshace aqui mismo: cuando este control no
// existia, cada pasada dejaba una inscripcion "INTENTO ..." ocupando casetas para siempre.
if (r.estado === 200 && r.cuerpo?.inscripcionId) {
  const ins = r.cuerpo.inscripcionId;
  const motivo = { motivo: 'limpieza de la prueba automatica' };
  const sol = await api(T, `/api/app/inscripciones/${ins}/solicitar-cancelacion`,
    { method: 'POST', body: JSON.stringify(motivo) });
  if (sol.cuerpo?.solicitudId) {
    await api(T, `/api/app/solicitudes-cancelacion/${sol.cuerpo.solicitudId}/aprobar`,
      { method: 'POST', body: JSON.stringify({}) });
  }
  await api(T, `/api/app/inscripciones/${ins}/cancelar`, { method: 'POST', body: JSON.stringify(motivo) });
  console.log('         (se deshizo la venta que no debio crearse)');
}



// ---- Lo que la pantalla Vendedores.vue lee de cada respuesta ----
// Estos fallaron de verdad: el desplegable de categorias salia vacio porque NO EXISTIA
// GET /api/app/categorias (solo habia POST/PATCH/DELETE, asi que la peticion caia en /error
// y volvia como un 302 al login), y la lista de casetas ponia "Sin categoria" en todas porque
// la vista leia p.categoria.nombre cuando categoria es un TEXTO en PuestoEstadoDTO.
r = await api(T,'/api/app/categorias');
ok(r.estado === 200 && Array.isArray(r.cuerpo), 'GET /categorias responde una lista', `HTTP ${r.estado}`);
ok(r.cuerpo?.[0]?.id != null && !!r.cuerpo?.[0]?.nombre, 'cada categoria trae id y nombre', r.cuerpo?.[0]?.nombre);

r = await api(T,'/api/app/puestos');
ok(typeof r.cuerpo?.[0]?.categoria === 'string', 'puesto.categoria es TEXTO, no objeto',
   JSON.stringify(r.cuerpo?.[0]?.categoria));

r = await api(T,'/api/app/vendedores');
const fila = r.cuerpo?.[0];
ok(!!fila?.persona?.nombre && !!fila?.rol?.nombre,
   'cada vendedor trae persona y rol (los pinta la tabla)', `${fila?.persona?.nombre} / ${fila?.rol?.nombre}`);


// ---- Asignacion por lote y exclusividad (el modal agrupado) ----
const otro = (await api(T,'/api/app/usuarios',{method:'POST',body:JSON.stringify({
  username:`vo${marca}`,password:'ClaveVendedor9',rolId:rolAdm.id,personaId:null,
  persona:{nombre:'OTRO',paterno:'VENDEDOR',materno:'',ci:`VO${marca}`,correo:'',celular:''}})})).cuerpo?.usuario?.id;

const libresIds = [...libres].filter(id => id !== obj.id).slice(0, 12);
r = await api(T,`/api/app/vendedores/${otro}/puestos`,{method:'PUT',body:JSON.stringify({puestoIds:libresIds})});
ok(r.estado===200 && r.cuerpo?.asignadas===libresIds.length,
   'PUT guarda un lote entero en una sola peticion', `asignadas=${r.cuerpo?.asignadas}`);

// Exclusividad: nadie mas puede tomarlas (es lo que hace que "a otro usuario ni le salgan").
r = await api(T,`/api/app/vendedores/${vid}/puestos`,{method:'PUT',body:JSON.stringify({puestoIds:libresIds.slice(0,3)})});
ok(r.cuerpo?.asignadas===0 && r.cuerpo?.noDisponibles?.length===3,
   'otro vendedor NO puede tomar casetas ya asignadas', r.cuerpo?.mensaje);

// Quitar y sumar en el MISMO guardado, que es lo que hace el boton Guardar del modal.
const mitad = libresIds.slice(0,6);
r = await api(T,`/api/app/vendedores/${otro}/puestos`,{method:'PUT',body:JSON.stringify({puestoIds:mitad})});
ok(r.cuerpo?.quitadas===libresIds.length-mitad.length && r.cuerpo?.asignadas===0,
   'un solo guardado quita las que sobran', `-${r.cuerpo?.quitadas}`);

// El catalogo dice de quien es cada una, que es lo que el modal usa para esconderlas.
r = await api(T,'/api/app/vendedores/puestos-asignables');
ok((r.cuerpo||[]).filter(p => p.asignadoAId === otro).length === mitad.length,
   'el catalogo marca a quien pertenece cada caseta', `${mitad.length} de ese vendedor`);

// Al dar de baja al vendedor, sus casetas vuelven al catalogo. Sin esto quedaban bloqueadas
// para siempre: la baja es logica, la fila del usuario se queda y la asignacion con ella.
await api(T,`/api/app/usuarios/${otro}`,{method:'DELETE'});
r = await api(T,'/api/app/vendedores/puestos-asignables');
ok((r.cuerpo||[]).filter(p => p.asignadoAId === otro).length === 0,
   'al eliminar un vendedor, sus casetas vuelven a estar disponibles');

await api(T,`/api/app/usuarios/${vid}`,{method:'DELETE'});
console.log(`\n== ${fallos === 0 ? 'Todo en orden' : fallos + ' fallo(s)'} ==\n`);
process.exit(fallos ? 1 : 0);
