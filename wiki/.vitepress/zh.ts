import { defineConfig, type DefaultTheme } from 'vitepress'

export const zh = defineConfig({
  lang: 'zh-Hans',
  description: '面向现代 Minecraft 的可配置多方块机器框架。',
  themeConfig: {
    nav: nav(),
    sidebar: sidebar(),
    editLink: {
      pattern: 'https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/edit/main/wiki/:path',
      text: '在 GitHub 上编辑此页面',
    },
    footer: {
      message: '基于 GNU GPL v3.0 许可证发布',
      copyright: '版权所有 © 2026-现在 HowXu',
    },
    docFooter: {
      prev: '上一页',
      next: '下一页',
    },
    outline: {
      label: '页面导航',
    },
  },
})

function nav(): DefaultTheme.NavItem[] {
  return [
    { text: '首页', link: '/zh-cn/' },
    { text: '入门', link: '/zh-cn/guides/intro' },
    { text: '用法', link: '/zh-cn/usage/machine-structure' },
    { text: '参考', link: '/zh-cn/reference/java-api' },
    { text: '示例', link: '/zh-cn/examples/basic-machine' },
    { text: '常见问题', link: '/zh-cn/guides/faq' },
  ]
}

function sidebar(): DefaultTheme.Sidebar {
  return [
    {
      text: '入门',
      items: [
        { text: '简介', link: '/zh-cn/guides/intro' },
        { text: '快速开始', link: '/zh-cn/guides/quick-start' },
        { text: '常见问题', link: '/zh-cn/guides/faq' },
      ],
    },
    {
      text: '用法',
      items: [
        { text: '机器结构', link: '/zh-cn/usage/machine-structure' },
        { text: '配方', link: '/zh-cn/usage/recipes' },
      ],
    },
    {
      text: '参考',
      items: [
        { text: 'Java API', link: '/zh-cn/reference/java-api' },
        { text: 'KubeJS API', link: '/zh-cn/reference/kubejs-api' },
      ],
    },
    {
      text: '示例',
      items: [{ text: '基础机器', link: '/zh-cn/examples/basic-machine' }],
    },
  ]
}
