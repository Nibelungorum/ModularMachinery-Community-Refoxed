import { readdirSync, readFileSync } from 'node:fs'
import { basename, dirname, extname, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, type DefaultTheme } from 'vitepress'

const locale = 'zh-cn'
const docsDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', locale)
const sectionTitles: Record<string, string> = {
  api: 'API 参考',
  quickstart: '快速开始',
  usage: '用法',
}

export const zh = defineConfig({
  lang: 'zh-Hans',
  description: '面向现代 Minecraft 的可配置多方块机器框架',
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
    { text: '快速开始', link: '/zh-cn/快速开始/开始' },
    { text: '全部示例', link: '/zh-cn/示例/开始' },
    { text: 'API 参考', link: '/zh-cn/API/开始' },
  ]
}

function sidebar(): DefaultTheme.Sidebar {
  return readdirSync(docsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .sort((left, right) => left.name.localeCompare(right.name))
    .map((section) => ({
      text: sectionTitles[section.name] ?? section.name,
      items: markdownFiles(resolve(docsDir, section.name)).map((file) => ({
        text: pageTitle(file),
        link: pageLink(file),
      })),
    }))
    .filter((section) => section.items.length > 0)
}

function markdownFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const path = resolve(directory, entry.name)
      if (entry.isDirectory()) {
        return markdownFiles(path)
      }
      return entry.isFile() && extname(entry.name) === '.md' ? [path] : []
    })
    .sort()
}

function pageTitle(file: string): string {
  const frontmatter = readFileSync(file, 'utf8').match(/^---\s*\n([\s\S]*?)\n---(?:\s*\n|$)/)?.[1]
  const title = frontmatter?.match(/^title:\s*(?:"([^"]*)"|'([^']*)'|(.+?))\s*$/m)
  return title?.[1] ?? title?.[2] ?? title?.[3]?.trim() ?? basename(file, '.md')
}

function pageLink(file: string): string {
  return `/${locale}/${relative(docsDir, file).split(sep).join('/').replace(/\.md$/, '')}`
}
