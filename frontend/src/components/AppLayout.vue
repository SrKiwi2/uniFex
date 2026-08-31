<script setup>
import { onUnmounted } from 'vue';
import AppShell from './AppShell.vue';
import { usePuestosStore } from '../stores/puestos';

// Vistas "vivas" que conservan estado (geometria, seleccion) al navegar.
// El resto se monta fresco en cada visita para traer datos actuales.
const vistasEnCache = ['Mapa', 'Editor', 'Board'];

// Este layout envuelve TODA la zona autenticada (el /login queda fuera), asi que se
// desmonta exactamente al cerrar sesion. Es el sitio correcto para soltar la conexion
// compartida en tiempo real: las vistas no pueden hacerlo porque viven en <KeepAlive>,
// donde onUnmounted no se dispara al navegar.
const puestos = usePuestosStore();
onUnmounted(() => puestos.desconectar());
</script>

<template>
  <AppShell>
    <router-view v-slot="{ Component }">
      <KeepAlive :include="vistasEnCache">
        <component :is="Component" />
      </KeepAlive>
    </router-view>
  </AppShell>
</template>
