<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // [{value, label, ...}]
  placeholder: { type: String, default: 'Seleccione…' },
  label: { type: String, default: '' },
  required: { type: Boolean, default: false },
  allowClear: { type: Boolean, default: true },
  searchKey: { type: String, default: 'label' }, // property to search in
  valueKey: { type: String, default: 'value' }, // property for value
  labelKey: { type: String, default: 'label' }, // property for label
  disabled: { type: Boolean, default: false },
});

const emit = defineEmits(['update:modelValue', 'change']);

const isOpen = ref(false);
const search = ref('');
const highlightedIndex = ref(-1);
const inputRef = ref(null);
const dropdownRef = ref(null);

const filteredOptions = computed(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return props.options;
  return props.options.filter(opt =>
    String(opt[props.searchKey] || '').toLowerCase().includes(q)
  );
});

const selectedOption = computed(() => {
  return props.options.find(opt => String(opt[props.valueKey]) === String(props.modelValue));
});

function toggleOpen() {
  if (props.disabled) return;
  isOpen.value = !isOpen.value;
  if (isOpen.value) {
    search.value = '';
    highlightedIndex.value = -1;
    nextTick(() => inputRef.value?.focus());
  }
}

function selectOption(opt) {
  const val = opt[props.valueKey];
  emit('update:modelValue', val);
  emit('change', val);
  isOpen.value = false;
  search.value = '';
  highlightedIndex.value = -1;
}

function clearSelection() {
  emit('update:modelValue', '');
  emit('change', '');
  isOpen.value = false;
}

function handleKeydown(e) {
  const opts = filteredOptions.value;
  if (!isOpen.value) {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) {
      e.preventDefault();
      isOpen.value = true;
      highlightedIndex.value = 0;
    }
    return;
  }
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault();
      highlightedIndex.value = Math.min(highlightedIndex.value + 1, opts.length - 1);
      break;
    case 'ArrowUp':
      e.preventDefault();
      highlightedIndex.value = Math.max(highlightedIndex.value - 1, 0);
      break;
    case 'Enter':
      e.preventDefault();
      if (highlightedIndex.value >= 0 && opts[highlightedIndex.value]) {
        selectOption(opts[highlightedIndex.value]);
      }
      break;
    case 'Escape':
      isOpen.value = false;
      search.value = '';
      highlightedIndex.value = -1;
      break;
    case 'Tab':
      isOpen.value = false;
      break;
  }
}

function handleClickOutside(e) {
  if (dropdownRef.value && !dropdownRef.value.contains(e.target)) {
    isOpen.value = false;
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside));
onUnmounted(() => document.removeEventListener('click', handleClickOutside));

watch(() => props.modelValue, () => {
  highlightedIndex.value = -1;
});
</script>

<template>
  <div class="autocomplete-select" ref="dropdownRef" :class="{ open: isOpen, disabled: disabled }">
    <label v-if="label" class="campo-label">{{ label }}</label>
    <div class="select-wrapper" @click="toggleOpen" @keydown="handleKeydown" tabindex="0">
      <div class="selected-value">
        <span v-if="selectedOption" class="selected-text">{{ selectedOption[labelKey] }}</span>
        <span v-else class="placeholder">{{ placeholder }}</span>
        <button
          v-if="allowClear && (selectedOption || modelValue)"
          class="clear-btn"
          @click.stop="clearSelection"
          aria-label="Limpiar"
        >✕</button>
      </div>
      <span class="chevron" :class="{ rotated: isOpen }">▼</span>
    </div>

    <div v-if="isOpen" class="dropdown">
      <input
        ref="inputRef"
        type="text"
        class="search-input"
        v-model="search"
        :placeholder="`Buscar ${label.toLowerCase()}...`"
        @click.stop
        @keydown="handleKeydown"
        @input="highlightedIndex = -1"
      />
      <ul class="options-list" role="listbox">
        <li
          v-for="(opt, idx) in filteredOptions"
          :key="opt[valueKey]"
          class="option"
          :class="{ highlighted: idx === highlightedIndex, selected: String(opt[valueKey]) === String(modelValue) }"
          role="option"
          :aria-selected="String(opt[valueKey]) === String(modelValue)"
          @click="selectOption(opt)"
          @mousemove="highlightedIndex = idx"
        >
          {{ opt[labelKey] }}
        </li>
        <li v-if="filteredOptions.length === 0" class="no-results">
          Sin resultados
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.autocomplete-select {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.campo-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text);
}
.select-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 0.8rem;
  border: 1px solid var(--border);
  border-radius: var(--radio-sm);
  background: var(--panel);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  min-height: 42px;
}
.select-wrapper:hover:not(.disabled) { border-color: var(--acento); }
.select-wrapper:focus-within {
  outline: none;
  border-color: var(--acento);
  box-shadow: 0 0 0 3px var(--acento-suave);
}
.select-wrapper.disabled { opacity: 0.6; cursor: not-allowed; }
.selected-value {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}
.selected-text { color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.placeholder { color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clear-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: var(--border);
  border-radius: 50%;
  color: var(--muted);
  font-size: 0.75rem;
  cursor: pointer;
  flex-shrink: 0;
  margin-left: 0.5rem;
}
.clear-btn:hover { background: var(--peligro); color: white; }
.chevron {
  font-size: 0.6rem;
  color: var(--muted);
  transition: transform 0.15s;
  flex-shrink: 0;
}
.chevron.rotated { transform: rotate(180deg); }
.dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radio-sm);
  box-shadow: var(--sombra-md);
  max-height: 280px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.search-input {
  width: 100%;
  padding: 0.5rem 0.7rem;
  border: none;
  border-bottom: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  font: inherit;
  outline: none;
}
.options-list {
  list-style: none;
  margin: 0;
  padding: 0.3rem 0;
  overflow-y: auto;
  max-height: 220px;
}
.option {
  padding: 0.5rem 0.8rem;
  cursor: pointer;
  transition: background 0.1s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.option:hover, .option.highlighted { background: var(--acento-suave); color: var(--acento); }
.option.selected { background: var(--acento-suave); color: var(--acento); font-weight: 600; }
.no-results { padding: 0.8rem; text-align: center; color: var(--muted); font-size: 0.85rem; }
</style>