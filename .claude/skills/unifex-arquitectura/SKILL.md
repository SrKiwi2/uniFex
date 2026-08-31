---
name: unifex-arquitectura
description: >-
  Refactoriza el proyecto UniFex con seguridad: borrar código muerto, renombrar clases y
  paquetes, consolidar duplicados, ordenar capas, corregir convenciones y pagar deuda técnica.
  Úsala SIEMPRE que el usuario pida limpiar, ordenar, reorganizar o mejorar la arquitectura;
  que mencione código muerto, clases o archivos "que no se usan", nombres inconsistentes,
  duplicados, imports sobrantes, mover algo de paquete, o retirar el frontend Thymeleaf viejo.
  Aplícala también antes de borrar CUALQUIER clase, método o archivo de este repositorio: aquí
  un símbolo sin referencias en Java suele estar vivo desde una plantilla Thymeleaf, un string
  SQL, una resolución de mismo-paquete o un .jrxml que ni siquiera está en el repo, y el
  compilador no te avisará. Para construir funcionalidades nuevas (casetas, mapa, reservas,
  endpoints) usa unifex-fullstack en su lugar.
---

# Refactorizar UniFex sin romperlo

Este repositorio tiene deuda real y vale la pena pagarla: 131 clases Java, dos frontends
conviviendo, una capa de servicios que es casi toda envoltura de DAOs, y nombres que no siguen
ninguna convención. Pero borrar aquí es más peligroso que en un proyecto normal, y por razones
específicas que conviene conocer antes de tocar nada.

El objetivo de esta skill no es frenarte. Es que el trabajo de limpieza **termine en un commit que
compila, arranca y sigue vendiendo casetas** en lugar de en una tarde de arqueología.

## Por qué "sin referencias" no significa muerto aquí

Cinco caminos por los que el código se usa sin que aparezca en una búsqueda ingenua. El primero
es el que más caro sale.

### 1. Resolución de mismo paquete: no hay `import` que encontrar

Existen **dos** `IServiceGenerico` idénticos: `model/IService/IServiceGenerico.java` y
`model/service/IServiceGenerico.java`. Parece obvio que uno sobra.

Busca `import com.usic.uniFex.model.IService.IServiceGenerico` y obtendrás **cero resultados**.
Conclusión tentadora: está muerto, bórralo. Pero las 12 interfaces que lo extienden viven en ese
mismo paquete y por eso **no necesitan importarlo**. Borrarlo rompe la compilación de medio
proyecto. (Las otras 4 interfaces sí importan explícitamente el de `model/service/`.)

`CLAUDE.md` describe mal este punto. No confíes en la documentación para decidir un borrado;
confía en una búsqueda que incluya el uso sin `import`:

```bash
# Mal: solo encuentra usos con import explícito.
grep -rn "import .*IServiceGenerico" src/main/java/

# Bien: encuentra también los del mismo paquete.
grep -rn "IServiceGenerico" src/main/java/
```

Regla general: **busca el identificador desnudo, en todo el repositorio, sin filtrar por
extensión.** El coste de leer resultados de más es minúsculo comparado con el de romper el build.

### 2. Thymeleaf referencia rutas, no clases

Las 36 plantillas de `resources/templates/` enlazan con `th:href="@{/venta/nuevo}"`, leen
atributos del modelo por nombre (`${inscripcion.entidad}`) e incluyen fragmentos por ruta. Nada de
eso lo ve el compilador de Java.

El caso testigo es `model/endpoint/PrecioController`: **cero referencias en todo el código Java**.
Parece un controlador huérfano. En realidad expone `GET /api/costo-puesto` y lo llama JavaScript
incrustado dentro de una plantilla:

```javascript
// src/main/resources/templates/publico/responsable.html:831
const resp = await fetch(`/api/costo-puesto?${params.toString()}`, { method: 'GET' });
```

Por lo tanto: **renombrar una clase de controlador es seguro; cambiar o borrar el `@GetMapping` que
expone no lo es.** Un método de controlador "sin llamadas" casi siempre lo llama una plantilla, y a
veces desde un `fetch()` dentro de un `<script>`, donde ni siquiera hay un `th:href` que buscar.
Antes de tocar un endpoint, busca su **URL** —no su nombre de clase— en `templates/` y en
`frontend/src/`.

### 3. Strings: SQL, funciones almacenadas y plantillas Jasper

- Las *stored functions* de PostgreSQL se invocan por nombre desde `model/repository/`:
  `fn_lista_puestos`, `fn_get_inscripciones`, `fn_inscripciones_por_categoria`,
  `obtenercostopuesto`. Renombrar la función en la BD sin tocar el string rompe en tiempo de
  ejecución, no de compilación.
- `@Query` con JPQL nombra **campos de la entidad**; `@Query(nativeQuery = true)` nombra
  **columnas de la BD**. Renombrar un campo Java rompe el primero en silencio hasta el arranque.
- `IServiceImp/UtilidadesServiceImpl` compila `.jrxml` en tiempo de ejecución desde un directorio
  `reportes/` **relativo al directorio de trabajo del proceso**. Esas plantillas **no están en el
  repositorio**. Los nombres de campo que consumen vienen de tus DTOs. Renombrar un campo de un DTO
  de reporte rompe un archivo que no puedes ver ni compilar.

### 4. Anotaciones y reflexión

Spring inyecta por tipo, JPA instancia entidades, Jackson serializa `record`s y getters. Un
constructor o getter "sin usos" puede ser el que usa el framework. En `PuestoEstadoDTO`, que es un
`record`, **el orden de los componentes es el orden del JSON** que consume la SPA.

### 5. Ramas por rol y por estado

`usuario.getRol().getNombre()` se compara contra `"ADMINISTRATIVO"` y `"VENDEDOR"`. El estado de
caseta se compara contra `"L"`, `"T"`, `"O"`, `"X"`. Un `if` que parece inalcanzable puede depender
de datos de producción.

## Protocolo de borrado seguro

Para cada símbolo o archivo candidato, en orden. No saltes al paso 5.

1. **Entiende qué es** antes de decidir. Un `@Controller` vacío no es lo mismo que un DTO huérfano.
2. **Busca el identificador desnudo en todo el repo**, sin filtro de extensión:
   `grep -rn "NombreDelSimbolo" .` — incluye `templates/`, `.sql`, `.properties`, `frontend/`.
3. **Según lo que sea, revisa su canal invisible:**
   - controlador o método con `@*Mapping` → busca la **URL** en `src/main/resources/templates/`
     y en `frontend/src/`.
   - entidad o campo → busca el nombre en `@Query`, en `db/reserva/*.sql` y considera los `.jrxml`
     ausentes.
   - interfaz o clase base → busca el nombre desnudo (mismo paquete, sin `import`).
   - propiedad de configuración → busca la clave en `application*.properties`.
4. **Compila**: `mvnw.cmd clean compile`. Necesario, no suficiente.
5. **Ejercita el runtime**, que es donde vive el 80 % del riesgo aquí:
   ```bash
   mvnw.cmd test -Dtest=PuestoReservaConcurrenciaTest
   mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
   node .claude/skills/unifex-fullstack/scripts/verificar-api.mjs
   ```
   Y si tocaste algo de la web vieja, abre la página real. `spring.thymeleaf.cache=false`, así que
   no hace falta reiniciar para ver un cambio de plantilla.
6. **Un commit por borrado**, con la evidencia en el mensaje ("sin referencias en templates/, sin
   ruta expuesta, `/` lo sirve HomeController"). Así revertir es barato cuando algo aparezca en
   producción tres semanas después.

Si un candidato requiere más de diez minutos de investigación para justificar su borrado, **déjalo
y anótalo**. El código muerto no cuesta nada al día; una regresión en la venta de casetas sí.

## Inventario verificado (2026-07-09)

Comprobado ejecutando las búsquedas, no leyendo la documentación. Es un **punto de partida, no un
sustituto del protocolo**: el repositorio se mueve y esta lista envejece. Reverifica antes de
borrar, y si encuentras algo que no está aquí, añádelo.

**Muerto, borrado seguro (evidencia comprobada):**

- `controller/login/LoginController.java` — `@Controller` de cuerpo vacío, con un `import
  GetMapping` sin usar. La ruta `/` la sirve `HomeController`, y `formLogin.loginPage("/")` apunta
  ahí. El login real es `POST /iniciar-sesion` en `AdminController`.
- `model/endpoint/ReciboPdfController.java` — su **único** endpoint está entero comentado
  (líneas 17-23), así que la clase no expone ninguna ruta. Ojo: borra el *controlador*, **no**
  `model/service/ReciboPdfService`, que sí se usa desde `AdminController`.
- Racimo de DTOs de pasarela: `model/dto/CompraDTO.java` (cero referencias),
  `model/dto/DatosCiente.java` (typo de "Cliente"; solo lo usa `CompraDTO`) y `model/dto/Item.java`
  de nivel superior (tanto `CompraDTO` como `PagoRequest` declaran su propia clase anidada `Item`,
  que **tapa** a la de nivel superior dentro de esos archivos). Los tres caen juntos.
- Racimo de DTOs de responsables: `model/dto/ResponsablesEditForm.java` y
  `model/dto/ResponsablePersonaDTO.java`. El único uso no comentado del segundo es un campo del
  primero, y el primero solo aparece en código comentado de `ResponsablesController`.
- `frontend/public/puntos-extraidos.json` — las 443 etiquetas auto-extraídas del PDF. El editor se
  reescribió como diseñador manual y ya nadie lo importa. Confírmalo con el usuario antes de
  borrarlo: costó una investigación y quizá lo quiera de referencia.

**Parece muerto pero está vivo — no lo borres:**

- `model/endpoint/PrecioController.java` — cero referencias en Java, pero expone
  `GET /api/costo-puesto`, que `templates/publico/responsable.html` llama con `fetch()`. Está mal
  ubicado (un controlador dentro de `model/`), así que **muévelo a `controller/`; no lo borres**.
- Los dos `IServiceGenerico`. Ver arriba. Son **idénticos**, así que la acción correcta no es
  borrar uno a ciegas sino **consolidar**: conserva `model/IService/IServiceGenerico.java` (12
  usuarios por mismo paquete), cambia el `import` en las 4 interfaces que apuntan al otro
  (`ICargoService`, `ICategoriaVentaService`, `IOficinaService`, `IVentaBoletoService`) y entonces
  borra `model/service/IServiceGenerico.java`. Compila para confirmar.
- `anotacion/@ValidarUsuarioAutenticado` — `CLAUDE.md` dice que no hace nada. **Ya no es cierto**:
  `Config/AutenticacionInterceptor`, registrado en `WebConfig`, la lee y redirige a `/` si no hay
  usuario en sesión. Siete controladores dependen de ella.

**Rama muerta:** en `AdminController` (~línea 471) hay un `else if ("VENDEDOR".equals(rol))`. **No
existe ningún rol `VENDEDOR`** en la tabla `rol` (son `SUPER USUARIO`, `ADMINISTRADOR`,
`ADMINISTRATIVO`, `CONTROL`, `ASESORIA`), así que esa rama es inalcanzable. Los "vendedores" del
negocio son los 35 usuarios `ADMINISTRATIVO`. Bórrala, pero comprueba primero qué devuelve el
`else` para no cambiar la respuesta de nadie.

**Bug latente, no código muerto:** `controller/puesto/GasetaController` devuelve la vista
`"administracion/gaseta/index"`, que no existe —las plantillas viven en `templates/gasetas/`. Esa
ruta revienta en tiempo de ejecución. No la "limpies" borrándola sin preguntar: puede ser una
pantalla a medio hacer que el usuario quiere terminar.

**`CLAUDE.md` está desactualizado.** Afirma que no hay WebSocket (lo hay), que la anotación de
autenticación no se aplica (se aplica) y describe mal cuál `IServiceGenerico` se usa. Corregirlo es
parte legítima del trabajo de arquitectura, y probablemente el cambio de mayor retorno por línea
editada, porque esa documentación dirige a todo el que llega después —incluido tú, la próxima vez.

## Convenciones de nombres

El proyecto es **íntegramente en español** —clases, rutas, columnas, comentarios— y esa coherencia
vale más que cualquier convención inglesa que puedas preferir. Mantenla.

Lo que sí rompe convención y conviene arreglar, con el criterio de que **renombrar símbolos Java es
seguro y renombrar URLs no lo es**:

| Actual | Debería ser | Riesgo |
|---|---|---|
| paquete `Config` | `config` | bajo (solo imports) |
| paquetes `model/IService`, `model/IServiceImp` | `model/service` (interfaces) e `impl` | medio: colisiona con el `model/service` actual; hazlo después de consolidar `IServiceGenerico` |
| paquete `controller/credencialesController` | `controller/credenciales` | bajo |
| clase `generadorCredenciales` | `GeneradorCredencialesController` | bajo |
| clase `ventaBoleteriaController` | `VentaBoleteriaController` | bajo |
| clase `GasetaController` | `CasetaController` o mejor, fusionar en `PuestoApiController` | bajo, pero verifica sus rutas |

Ojo con `GasetaController`: "gaseta" no es palabra. El dominio dice `Puesto` en la entidad, "caseta"
en la conversación y "gaseta" en esa clase. Unificar el vocabulario del dominio —decidir si son
*puestos* o *casetas* y usar una sola palabra— es más valioso que cualquier movimiento de archivos,
porque los nombres son la interfaz que el próximo desarrollador lee primero. Consúltalo con el
usuario antes: es una decisión suya, no tuya.

En Java, un renombrado de clase o paquete no rompe Thymeleaf mientras las rutas (`@GetMapping`) no
cambien. Ese es el motivo por el que estos cambios son baratos. Aprovecha eso.

## Qué NO tocar

Estas cosas parecen mejorables y no lo son. Cada una está así por una razón que costó depurar.
Antes de "arreglar" cualquiera, lee `unifex-fullstack`.

- **Los `UPDATE` condicionales nativos de `IPuestoDao`.** Parecen SQL crudo evitable en un proyecto
  con JPA. Son la única cosa que impide vender dos veces la misma caseta: la condición viaja con la
  escritura y la BD arbitra. Convertirlos a "leer, comprobar, guardar" reintroduce exactamente el
  bug que eliminan.
- **`@Modifying(clearAutomatically = true)`** en esos métodos. Sin él, el contexto de persistencia
  devuelve el estado viejo.
- **`PuestoEventPublisher` como único punto de publicación.** No lo distribuyas ni añadas topics.
- **Los dos `SecurityFilterChain` y sus `AntPathRequestMatcher`.** El `MvcRequestMatcher` "más
  idiomático" no capturaba `GET /api/app/puestos`. Tampoco cambies `setStatus` por `sendError`:
  produce un 302 al login donde debe haber un 401.
- **`@Column(name = "mapa_x")` / `@Column(name = "mapa_y")` explícitos** en `Puesto`. La estrategia
  de nombres de Hibernate genera `mapax` y el arranque falla con "no existe la columna".
- **`JwtAuthFilter` sin `@Component`.** Se instancia a mano para que aplique solo a la cadena API.
- **`inscripcion.id_edicion DEFAULT 2`.** Etiqueta las inscripciones nuevas con la edición 2026 sin
  código Java. Quitarlo rompe el versionado por edición en silencio.
- **Las stored functions.** No reimplementes su lógica en Java "para tenerla junta": crearías dos
  fuentes de verdad. Si hay que moverla, muévela entera y borra la función.
- **`static/assets/` (52 MB, 58 librerías vendor del tema Sneat).** Casi todo es relleno, pero solo
  lo consume la web Thymeleaf. Purgarlo es trabajo perdido si la web se va a retirar de todos modos;
  hazlo al retirar Thymeleaf, no antes.

## Refactors por orden de retorno

Si te piden "mejora la arquitectura" sin más concreción, esta es la secuencia sensata. Propón,
no ejecutes todo de golpe: cada punto es un commit revisable.

1. **Corregir `CLAUDE.md`.** Es la fuente de verdad de todo el que llegue después, y hoy miente en
   tres puntos. Coste: minutos.
2. **Borrar el código muerto confirmado**: `LoginController`, `ReciboPdfController` y los dos
   racimos de DTOs. Evidencia completa, riesgo nulo, un commit por racimo.
3. **Consolidar `IServiceGenerico`** siguiendo el procedimiento de arriba.
4. **Normalizar nombres de clases y paquetes** (tabla de arriba), sin tocar rutas. Incluye mover
   `PrecioController` de `model/endpoint/` a `controller/` —está vivo, solo mal ubicado— y
   entonces el paquete `model/endpoint` desaparece solo.
5. **Unificar el vocabulario puesto/caseta/gaseta**, tras acordarlo con el usuario.
6. **Adelgazar la capa de servicios.** `model/IService` + `model/IServiceImp` son 37 archivos que en
   su mayoría delegan al DAO sin añadir nada. Una interfaz por entidad cuya única implementación es
   `return dao.findAll()` no está desacoplando nada; está duplicando el DAO. Ojo: los servicios con
   lógica real (`PuestoReservaService`, `CategoriaMapaService`, `FileStorageService`,
   `ReciboPdfService`) se quedan. Este cambio es grande; propón un piloto con una entidad y mide.
7. **Retirar Thymeleaf**, ruta por ruta, cuando la SPA cubra cada flujo. Es la Fase 5 del plan y
   solo entonces se pueden borrar `templates/` y los 52 MB de `static/assets/`.

Los pasos 1–4 son seguros y aportan claridad inmediata. Del 5 en adelante, habla con el usuario
antes: afectan al dominio o al alcance, y ninguna de esas decisiones es técnica.
