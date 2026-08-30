import type { InjectionKey, Ref } from 'vue'

export interface TabRecord {
  key: symbol
  label: string
  tabId: string
  panelId: string
}

export interface TabsContext {
  tabs: Ref<TabRecord[]>
  activeTab: Ref<symbol | null>
  registerTab: (key: symbol, label: string) => void
  unregisterTab: (key: symbol) => void
  activate: (key: symbol) => void
}

export const tabsKey: InjectionKey<TabsContext> = Symbol('mmcr-tabs')
