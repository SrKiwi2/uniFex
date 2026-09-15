<script setup>
/**
 * Los anuncios de administracion, arriba a la derecha.
 *
 * <h2>Por que no es un modal</h2>
 * Esto llega mientras alguien esta registrando una venta con un cliente delante. Un dialogo que
 * tape la pantalla obligaria a cerrarlo para seguir, y lo que se cierra con prisa no se lee. Es
 * una tarjeta grande, visible, que NO bloquea: se puede seguir vendiendo con ella puesta.
 *
 * <h2>Por que se queda hasta que se cierra</h2>
 * No se desvanece sola como un toast. Un aviso de "dejen de vender la zona C" que desaparece a
 * los tres segundos es exactamente el que no se ve. Cerrarlo es un acto del usuario, y se
 * recuerda para no volver a interrumpirle con lo mismo.
 *
 * Va montado UNA vez en `AppShell`, asi que sale en cualquier pantalla: mapa, registro de venta,
 * credenciales. Ese era el pedido.
 */
import { useAnunciosStore } from '../stores/anuncios';

const anuncios = useAnunciosStore();

const ICONO = { urgente: '🚨', aviso: '⚠️', info: 'ℹ️' };

/** "14:05" — la hora a secas basta: los anuncios se leen el mismo dia en que se publican. */
const hora = (t) => {
  if (!t) return '';
  try {
    return new Date(t).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
};
</script>

<template>
  <!-- `aria-live` para que un lector de pantalla lo anuncie sin robar el foco: robarlo
       interrumpiria a quien esta escribiendo el C.I. de un cliente. -->
  <div class="pila-anuncios" role="status" aria-live="polite">
    <TransitionGroup name="anuncio">
      <article v-for="a in anuncios.visibles" :key="a.id"
               class="anuncio" :class="`nivel-${a.nivel || 'info'}`">
        <span class="icono" aria-hidden="true">{{ ICONO[a.nivel] || ICONO.info }}</span>
        <div class="cuerpo">
          <strong v-if="a.titulo" class="titulo">{{ a.titulo }}</strong>
          <p class="mensaje">{{ a.mensaje }}</p>
          <span class="firma muted">
            {{ a.publicadoPor || 'Administración' }}<template v-if="hora(a.publicado)"> · {{ hora(a.publicado) }}</template>
          </span>
        </div>
        <button class="cerrar" title="Cerrar este anuncio" aria-label="Cerrar este anuncio"
                @click="anuncios.cerrar(a.id)">✕</button>
      </article>
    </TransitionGroup>
  </div>
</template>

<style scoped>
/*
 * Arriba a la derecha y por encima de casi todo, pero POR DEBAJO del velo de carga (1500) y del
 * modal de aviso (2000): si algo esta trabajando o preguntando, eso manda.
 *
 * `pointer-events: none` en la pila y `auto` en cada tarjeta: el hueco vacio de la columna no
 * puede comerse los toques del mapa que hay detras.
 */
.pila-anuncios {
  position: fixed;
  top: calc(0.75rem + var(--safe-top, 0px));
  right: calc(0.75rem + var(--safe-right, 0px));
  z-index: 1400;
  display: flex; flex-direction: column; gap: 0.6rem;
  width: min(380px, calc(100vw - 1.5rem));
  pointer-events: none;
}

.anuncio {
  pointer-events: auto;
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 0.6rem;
  padding: 0.8rem 0.9rem;
  border-radius: var(--radio, 12px);
  background: var(--panel, #fff);
  border-left: 5px solid var(--color-nivel, var(--acento));
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.22);
}
.nivel-info    { --color-nivel: var(--acento, #2563eb); }
.nivel-aviso   { --color-nivel: var(--tramite, #d97706); }
.nivel-urgente { --color-nivel: var(--danger, #dc2626); }

/* El urgente late despacio: llama la atencion sin marear a quien tiene la pantalla delante
   durante horas. Se respeta a quien pidió menos animación. */
.nivel-urgente { animation: latido 2.4s ease-in-out infinite; }
@keyframes latido {
  0%, 100% { box-shadow: 0 10px 30px rgba(2, 6, 23, 0.22); }
  50%      { box-shadow: 0 10px 30px rgba(220, 38, 38, 0.45); }
}
@media (prefers-reduced-motion: reduce) {
  .nivel-urgente { animation: none; }
}

.icono { font-size: 1.25rem; line-height: 1.2; }
.cuerpo { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
.titulo { font-size: 1rem; }
.mensaje { margin: 0; line-height: 1.45; overflow-wrap: anywhere; }
.firma { font-size: 0.78rem; }

.cerrar {
  align-self: start; border: none; background: transparent; cursor: pointer;
  font-size: 1rem; line-height: 1; color: inherit; opacity: 0.55;
  /* Area de toque de dedo: en el telefono una ✕ de 16 px no se acierta. */
  min-width: 32px; min-height: 32px;
}
.cerrar:hover { opacity: 1; }

.anuncio-enter-active, .anuncio-leave-active { transition: opacity 0.25s, transform 0.25s; }
.anuncio-enter-from, .anuncio-leave-to { opacity: 0; transform: translateX(20px); }

@media (max-width: 480px) {
  /* En el telefono ocupa el ancho util, pero sigue arriba y sigue sin bloquear. */
  .pila-anuncios { left: calc(0.75rem + var(--safe-left, 0px)); width: auto; }
}
</style>
