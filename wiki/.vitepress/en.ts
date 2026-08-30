import { defineConfig, type DefaultTheme } from 'vitepress'

export const en = defineConfig({
  lang: 'en-US',
  description: 'A configurable multiblock machine framework for modern Minecraft.',
  themeConfig: {
    nav: nav(),
    sidebar: sidebar(),
    editLink: {
      pattern: 'https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/edit/main/wiki/:path',
      text: 'Edit this page on GitHub',
    },
    footer: {
      message: 'Released under the GNU GPL v3.0 License',
      copyright: 'Copyright © 2026-present HowXu',
    },
  },
})

function nav(): DefaultTheme.NavItem[] {
  return [
    { text: 'Home', link: '/en-us/' },
    { text: 'Guide', link: '/en-us/guides/intro' },
    { text: 'Usage', link: '/en-us/usage/machine-structure' },
    { text: 'Reference', link: '/en-us/reference/java-api' },
    { text: 'Examples', link: '/en-us/examples/basic-machine' },
    { text: 'FAQ', link: '/en-us/guides/faq' },
  ]
}

function sidebar(): DefaultTheme.Sidebar {
  return [
    {
      text: 'Guide',
      items: [
        { text: 'Introduction', link: '/en-us/guides/intro' },
        { text: 'Quick Start', link: '/en-us/guides/quick-start' },
        { text: 'FAQ', link: '/en-us/guides/faq' },
      ],
    },
    {
      text: 'Usage',
      items: [
        { text: 'Machine Structure', link: '/en-us/usage/machine-structure' },
        { text: 'Recipes', link: '/en-us/usage/recipes' },
      ],
    },
    {
      text: 'Reference',
      items: [
        { text: 'Java API', link: '/en-us/reference/java-api' },
        { text: 'KubeJS API', link: '/en-us/reference/kubejs-api' },
      ],
    },
    {
      text: 'Examples',
      items: [{ text: 'Basic Machine', link: '/en-us/examples/basic-machine' }],
    },
  ]
}
