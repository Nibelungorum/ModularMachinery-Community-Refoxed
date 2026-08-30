import { defineConfig } from 'vitepress'
import { en } from './en'
import { zh } from './zh'

export default defineConfig({
  title: 'MMCR Wiki',
  description: 'Documentation for Modular Machinery Community: Refoxed.',
  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/logo.png' }],
  ],
  locales: {
    'en-us': {
      label: 'English',
      link: '/en-us/',
      ...en,
    },
    'zh-cn': {
      label: '简体中文',
      link: '/zh-cn/',
      ...zh,
    },
  },
  markdown: {
    lineNumbers: true,
  },
  themeConfig: {
    logo: {
      src: '/logo.png',
      alt: 'MMCR',
    },
    siteTitle: 'MMCR Wiki',
    search: {
      provider: 'local',
    },
    socialLinks: [
      {
        icon: 'github',
        link: 'https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed',
      },
    ],
  },
})
