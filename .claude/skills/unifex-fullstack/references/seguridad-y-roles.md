# Seguridad, JWT y roles

## Dos cadenas de filtros, deliberadamente separadas

`Config/SecurityConfig` define dos `SecurityFilterChain` y el orden es la razón de que ambas
convivan sin pisarse:

**`apiSecurityFilterChain` `@Order(1)`** captura `/api/auth/**` y `/api/app/**`. Es *stateless*,
sin CSRF, autentica con JWT y responde con códigos HTTP + JSON.

**`webSecurityFilterChain` `@Order(2)`** recoge todo lo demás: es el sitio Thymeleaf de siempre,
con sesión y `formLogin`. Su lista `permitAll()` deja pasar casi todo, así que **Spring Security
prácticamente no protege las páginas web**; la autenticación real de esas rutas se comprueba en
código (`AutenticacionInterceptor` + lecturas de `HttpSession`).

Consecuencia práctica: un endpoint nuevo bajo `/api/app/**` nace protegido; uno bajo cualquier
otra ruta nace **abierto**. Si añades una API, ponla bajo `/api/app`.

## Las dos trampas de la cadena API

**El matcher.** `securityMatcher` con un `MvcRequestMatcher` y patrón `/**` no capturaba
`GET /api/app/puestos` (tres segmentos) aunque sí capturaba rutas más profundas. Por eso el
código usa rutas puras:

```java
.securityMatcher(new OrRequestMatcher(
        AntPathRequestMatcher.antMatcher("/api/auth/**"),
        AntPathRequestMatcher.antMatcher("/api/app/**")))
```

Si agregas un prefijo nuevo, agrégalo aquí con `antMatcher` **y prueba la ruta corta sin
subrutas**, que es la que fallaba.

**`sendError` no.** Un `sendError` dentro de la cadena API dispara un forward interno a `/error`,
que la cadena web atiende y redirige al login: recibes un **302 donde esperabas un 401**. Por eso
los handlers escriben la respuesta a mano:

```java
res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
res.setContentType("application/json;charset=UTF-8");
res.getWriter().write("{\"ok\":false,\"mensaje\":\"No autenticado\"}");
```

Si alguna vez ves un 302 en una llamada de la SPA, sospecha de esto o de un 500 que se está
redirigiendo a `/error`.

## El JWT

`POST /api/auth/login` con `{usuario, contrasena}` devuelve `{ok, token, usuario, rol}`.
`security/JwtService` firma HS256 (jjwt 0.12.6) con `unifex.jwt.secret`; el token dura 24 h por
defecto (`unifex.jwt.expiration-ms`) y lleva `sub` (username), `uid` (id) y `rol`.

`security/JwtAuthFilter` **no es un `@Component`**: se instancia dentro de `SecurityConfig` y se
registra solo en la cadena API. Si lo anotaras como componente, Spring Boot lo registraría además
como filtro global del servlet y también correría sobre las páginas web.

Un token inválido o expirado **no aborta la petición**: el filtro limpia el contexto y sigue, y es
el endpoint protegido quien produce el 401/403. Eso mantiene los endpoints públicos accesibles con
un token basura, que es lo correcto.

En el controlador, el usuario actual sale del contexto, nunca de la sesión:

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
Long usuarioId = (auth != null && auth.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
```

En el frontend, `src/api.js` (`apiFetch`) añade el `Bearer` y, ante un 401, hace `logout()` y
lanza. El token vive en `localStorage` vía el store `stores/auth.js`.

## Roles: cuáles existen de verdad

Esta es la lista completa, sacada de la tabla `rol`, con el número de usuarios:

| Rol | Usuarios | Qué es |
|---|---|---|
| `ADMINISTRATIVO` | 35 | **los vendedores**: venden casetas |
| `SUPER USUARIO` | 1 | `admin1` |
| `ADMINISTRADOR` | 1 | administración |
| `CONTROL` | 1 | control de acceso |
| `ASESORIA` | 1 | — |

**No existe ningún rol `VENDEDOR`.** El `else if ("VENDEDOR".equals(rol))` de `AdminController` es
una rama muerta. Es un error fácil de repetir porque el usuario los llama "vendedores": en el
lenguaje del negocio son vendedores, en la base son `ADMINISTRATIVO`. Comprueba la tabla antes de
escribir un nombre de rol.

`JwtAuthFilter` concede la autoridad `ROLE_<rol>` normalizado (mayúsculas, espacios → guiones
bajos), así que `SUPER USUARIO` llega como `ROLE_SUPER_USUARIO`. Esa conversión vive en un único
sitio, `JwtUser.rolNormalizado()`, y la comparten el filtro HTTP y el interceptor del WebSocket.

Las expresiones viven en `security/Roles`, no repartidas por los controladores:

```java
// security/Roles.java
public static final String EDITA_PLANO = "hasAnyRole('SUPER_USUARIO','ADMINISTRADOR')";

// uso: sobre la clase si TODO el controlador es de administración,
// o sobre el método si conviven operaciones de venta y de edición.
@PreAuthorize(Roles.EDITA_PLANO)
```

**`@EnableMethodSecurity` es obligatorio** (ya está en `SecurityConfig`). Sin él, `@PreAuthorize`
se ignora **en silencio**: el código parece protegido y el endpoint está abierto. Es el fallo más
peligroso de esta zona porque no produce ningún error.

Hoy están protegidas la creación/edición de categorías y el guardado de posiciones. Vender
(`reservar`/`liberar`/`confirmar`) sigue siendo de cualquier usuario autenticado, que es lo
correcto: los 35 `ADMINISTRATIVO` deben poder vender.

Para comprobar un 403 sin conocer la contraseña de un vendedor, acuña un JWT con el secreto de dev
(`unifex.jwt.secret` en `application-dev.properties`) y el claim `rol: "ADMINISTRATIVO"`. Es la
forma barata de probar la autorización de verdad y no solo de leerla.

## `/ws` exige JWT en el CONNECT

`Config/WebSocketConfig` registra un `ChannelInterceptor` que valida el token en el frame STOMP
`CONNECT` y rechaza la conexión si falta o no es válido.

Se hace en el `CONNECT` y **no en el handshake HTTP** por SockJS: cuando cae al transporte de
sondeo no puede poner una cabecera `Authorization` en el handshake, así que validar ahí dejaría
fuera a clientes legítimos. El frame `CONNECT`, en cambio, siempre lleva lo que el cliente le pase
en `connectHeaders`.

Del lado Vue, `src/ws.js` manda `connectHeaders: { Authorization: 'Bearer …' }` y expone un
callback `onRechazo`; las vistas lo usan para cerrar la sesión, porque un mapa sin tiempo real
muestra casetas libres que ya se vendieron.

Nota: `/ws/info` (el endpoint de descubrimiento de SockJS) sigue siendo público y devuelve 200 sin
token. No expone datos; solo dice qué transportes hay.

## Secretos

`application.properties` ya **no** trae valores: `DB_PASSWORD`, `PASARELA_KEY`, `API_KEY` y
`JWT_SECRET` se leen de variables de entorno **sin valor por defecto**, así que producción no
arranca si faltan. El perfil `dev` sí lleva valores locales para que dev y los tests funcionen sin
configurar nada.

Los secretos anteriores están commiteados en el historial de git. **Deben rotarse**; quitarlos del
archivo actual no los borra del historial.

## La API vieja `/api`

`controller/api/ApiController` autentica comparando el header `X-API-KEY` con la propiedad
`api.key`, a mano. Es independiente del JWT y sirve a integraciones externas. No la mezcles con
`/api/app`, que es la de la SPA.
