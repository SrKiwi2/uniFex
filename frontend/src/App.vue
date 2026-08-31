<script setup>
import { onMounted } from 'vue';
import ToastHost from './components/ToastHost.vue';
import { iniciarTema } from './ui/tema';

onMounted(iniciarTema);
</script>

<template>
  <router-view />
  <ToastHost />
</template>
