<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  metrics: Array<{ key: string, label: string, value: number }>
}>()

const cards = computed(() => props.metrics)
</script>

<template>
  <div class="dashboard-metrics">
    <article v-for="metric in cards" :key="metric.key" class="dashboard-card" :data-testid="`metric-${metric.key}`">
      <p class="dashboard-card__label">{{ metric.label }}</p>
      <strong class="dashboard-card__value">{{ metric.value }}</strong>
    </article>
  </div>
</template>
