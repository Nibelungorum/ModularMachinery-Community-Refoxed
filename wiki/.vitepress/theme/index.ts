import Theme from 'vitepress/theme'
import Tabs from './Tabs.vue'
import TabItem from './TabItem.vue'
import './custom.css'

export default {
  extends: Theme,
  enhanceApp({ app }) {
    app.component('Tabs', Tabs)
    app.component('TabItem', TabItem)
  },
}
