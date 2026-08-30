<template>
  <div
    v-if="isActive"
    class="tab-item"
    role="tabpanel"
    :id="panelId"
    :aria-labelledby="tabId"
    tabindex="0"
  >
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed, inject, onUnmounted } from 'vue'
import { tabsKey } from './tabs-context'

const props = defineProps<{
  label: string
}>()

const context = inject(tabsKey)
if (!context) {
  throw new Error('TabItem must be used inside a Tabs component.')
}

const tabKey = Symbol(`mmcr-tab-${props.label}`)
context.registerTab(tabKey, props.label)
onUnmounted(() => context.unregisterTab(tabKey))

const tab = computed(() => context.tabs.value.find((item) => item.key === tabKey))
const isActive = computed(() => context.activeTab.value === tabKey)
const tabId = computed(() => tab.value?.tabId)
const panelId = computed(() => tab.value?.panelId)
</script>

<style scoped>
.tab-item {
  min-width: 0;
}
</style>
