/** S1 定稿：一级 / 二级导航中文文案与路由匹配 */

export type NavItem = {
  to: string
  label: string
  match: (path: string) => boolean
}

export type SubNavItem = NavItem & {
  adminOnly?: boolean
}

export const PRIMARY_NAV: NavItem[] = [
  { to: '/home', label: '首页', match: (p) => p === '/home' },
  { to: '/about', label: '探索', match: (p) => p === '/about' },
  { to: '/diary', label: '日记', match: (p) => p.startsWith('/diary') },
  {
    to: '/recommend',
    label: '游览',
    match: (p) =>
      p === '/recommend' ||
      p === '/scenic' ||
      p.startsWith('/scenic/') ||
      p.startsWith('/admin'),
  },
]

/** 游览分区二级导航：推荐 + 景区工作台 + 管理（日记仅从顶部栏进入） */
export const GALLERY_SUB_NAV: SubNavItem[] = [
  { to: '/recommend', label: '推荐', match: (p) => p === '/recommend' },
  { to: '/scenic', label: '景区', match: (p) => p === '/scenic' || p.startsWith('/scenic/') },
  { to: '/admin', label: '管理', match: (p) => p.startsWith('/admin'), adminOnly: true },
]

export const GALLERY_SECTION_TITLE = '游览'

export function isGallerySection(path: string): boolean {
  return PRIMARY_NAV.find((n) => n.to === '/recommend')!.match(path)
}

export function isSubNavActive(item: NavItem, path: string): boolean {
  return item.match(path)
}
