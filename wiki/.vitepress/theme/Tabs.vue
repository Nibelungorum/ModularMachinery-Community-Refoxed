<template>
  <div class="tabs-container">
    <div class="tabs-header" role="tablist" aria-label="Tabs">
      <button
        v-for="(tab, index) in tabs"
        :id="tab.tabId"
        :key="tab.tabId"
        :ref="(element) => setButtonRef(element, index)"
        class="tab-button"
        :class="{ active: activeTab === tab.key }"
        type="button"
        role="tab"
        :aria-selected="activeTab === tab.key"
        :aria-controls="tab.panelId"
        :tabindex="activeTab === tab.key ? 0 : -1"
        @click="activate(tab.key)"
        @keydown="onKeydown($event, index)"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="tabs-content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  nextTick,
  provide,
  ref,
  useId,
} from 'vue'
import type { ComponentPublicInstance } from 'vue'
import { tabsKey, type TabRecord } from './tabs-context'

const instanceId = useId()
const tabs = ref<TabRecord[]>([])
const activeTab = ref<symbol | null>(null)
const buttonRefs = ref<(HTMLButtonElement | null)[]>([])

function registerTab(key: symbol, label: string) {
  if (tabs.value.some((tab) => tab.key === key)) return

  const index = tabs.value.length
  tabs.value.push({
    key,
    label,
    tabId: `${instanceId}-tab-${index}`,
    panelId: `${instanceId}-panel-${index}`,
  })

  if (activeTab.value === null) {
    activeTab.value = key
  }
}

function unregisterTab(key: symbol) {
  tabs.value = tabs.value.filter((tab) => tab.key !== key)
  if (activeTab.value === key) {
    activeTab.value = tabs.value[0]?.key ?? null
  }
}

function activate(key: symbol) {
  if (tabs.value.some((tab) => tab.key === key)) {
    activeTab.value = key
  }
}

function setButtonRef(
  element: Element | ComponentPublicInstance | null,
  index: number,
) {
  buttonRefs.value[index] = element instanceof HTMLButtonElement ? element : null
}

function focusTab(index: number) {
  const tab = tabs.value[index]
  if (!tab) return

  activate(tab.key)
  nextTick(() => buttonRefs.value[index]?.focus())
}

function onKeydown(event: KeyboardEvent, index: number) {
  let nextIndex: number | undefined

  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    nextIndex = (index + 1) % tabs.value.length
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    nextIndex = (index - 1 + tabs.value.length) % tabs.value.length
  } else if (event.key === 'Home') {
    nextIndex = 0
  } else if (event.key === 'End') {
    nextIndex = tabs.value.length - 1
  }

  if (nextIndex !== undefined && tabs.value.length > 0) {
    event.preventDefault()
    focusTab(nextIndex)
  }
}

provide(tabsKey, {
  tabs,
  activeTab,
  registerTab,
  unregisterTab,
  activate,
})
</script>

<style scoped>
.tabs-container {
  margin: 0.8rem 0;
  overflow: hidden;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
}

.tabs-header {
  display: flex;
  flex-wrap: wrap;
  border-bottom: 1px solid var(--vp-c-divider);
  background-color: var(--vp-c-bg-soft);
}

.tab-button {
  flex: 0 1 auto;
  max-width: 100%;
  padding: 8px 12px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--vp-c-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  text-align: left;
  overflow-wrap: anywhere;
  transition: color 0.2s ease, background-color 0.2s ease, border-color 0.2s ease;
}

.tab-button:hover,
.tab-button:focus-visible,
.tab-button.active {
  color: var(--vp-c-brand-1);
}

.tab-button:hover,
.tab-button:focus-visible {
  background-color: var(--vp-c-bg-mute);
  outline: none;
}

.tab-button.active {
  border-bottom-color: var(--vp-c-brand-1);
  background-color: var(--vp-c-bg);
}

.tabs-content {
  padding: 12px 14px;
  overflow-x: auto;
  background-color: var(--vp-c-bg);
}

@media (max-width: 640px) {
  .tab-button {
    padding: 8px 10px;
    font-size: 12px;
  }

  .tabs-content {
    padding: 10px 12px;
  }
}
</style>
