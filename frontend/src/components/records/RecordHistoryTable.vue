<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { FieldDefinition, SubType } from '../../api/metadata'
import type { HistorySnapshot } from '../../api/records'
import RecordStatusTag from './RecordStatusTag.vue'
import RecordSnapshotTables from './RecordSnapshotTables.vue'

withDefaults(defineProps<{
  snapshots: HistorySnapshot[]
  fields: FieldDefinition[]
  subTypes?: SubType[]
  subFields?: Record<number, FieldDefinition[]>
}>(), { subTypes: () => [], subFields: () => ({}) })
const { t } = useI18n()

</script>

<template>
  <div class="record-history">
    <article
      v-for="snapshot in snapshots.slice(0, 3)"
      :key="snapshot.version"
      class="record-history__version"
      :data-testid="`history-version-${snapshot.version}`"
    >
      <header class="record-history__header">
        <h3>{{ t('record.history.version', { version: snapshot.version }) }}</h3>
        <RecordStatusTag :status="snapshot.status" />
      </header>
      <RecordSnapshotTables
        :snapshot="snapshot"
        :master-fields="fields"
        :sub-types="subTypes"
        :sub-fields="subFields"
        :testid-prefix="`history-${snapshot.version}`"
      />
    </article>
  </div>
</template>
