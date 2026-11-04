/* ============================================================
   API网关平台 Demo · 公共布局（顶栏 + 侧边导航）
   纯静态渲染，无后端依赖
   ============================================================ */

/* 侧边栏导航菜单数据 */
const NAV_GROUPS = [
  {
    /* 第一组不显示组标题：一级菜单直接平铺 */
    title: '',
    items: [
      { href: 'index.html', label: '网关总览', key: 'dashboard', icon: 'home', status: '已运行' },
      { href: 'pages/service-governance.html', label: '服务发现', key: 'service', icon: 'server', status: '已运行' },
      { href: 'pages/trace-log.html', label: '日志追踪', key: 'trace', icon: 'trace', status: '已运行' }
    ]
  },
  {
    title: '接入中心',
    items: [
      { href: 'pages/system-onboard.html', label: '系统接入', key: 'onboard', icon: 'cpu' },
      { href: 'pages/auto-onboard.html', label: '自动接入', key: 'auto', icon: 'bolt' },
      { href: 'pages/go-live.html', label: '上线发布', key: 'golive', icon: 'rocket' }
    ]
  },
  {
    title: '系统管理',
    items: [
      { href: 'pages/user-management.html', label: '用户管理', key: 'user', icon: 'user' }
    ]
  }
];

const ICONS = {
  home: '<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2h-4v-7h-6v7H5a2 2 0 0 1-2-2V9z"/>',
  route: '<circle cx="6" cy="19" r="3"/><circle cx="18" cy="5" r="3"/><path d="M9 19h4a4 4 0 0 0 4-4V8"/><polyline points="14 4 18 4 18 8"/>',
  plug: '<path d="M9 2v6M15 2v6M5 8h14v3a6 6 0 0 1-6 6h-2a6 6 0 0 1-6-6V8zM12 17v5"/>',
  cpu: '<rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="2" x2="9" y2="4"/><line x1="15" y1="2" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="22"/><line x1="15" y1="20" x2="15" y2="22"/><line x1="20" y1="9" x2="22" y2="9"/><line x1="20" y1="15" x2="22" y2="15"/><line x1="2" y1="9" x2="4" y2="9"/><line x1="2" y1="15" x2="4" y2="15"/>',
  doc: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>',
  server: '<rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  trace: '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>',
  space: '<path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
  ship: '<path d="M12 2L2 7v10l10 5 10-5V7l-10-5z"/><path d="M5 9h14v6H5z"/><path d="M12 2v20"/>',
  chart: '<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/><line x1="3" y1="20" x2="21" y2="20"/>',
  search: '<circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="8" y1="11" x2="14" y2="11"/>',
  rocket: '<path d="M4 14c0-4 4-8 10-9 1 6-3 10-7 10H4z"/><path d="M9 19l2-3"/><path d="M14 5c3 1 5 4 5 7"/><circle cx="15" cy="16" r="3"/><path d="M13 18l-4 4"/>',
  flow: '<path d="M12 2v6"/><path d="M12 16v6"/><path d="M8 6h8v6H8z"/><path d="M8 14h8v6H8z"/>',
  bolt: '<path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>',
  wallet: '<path d="M21 8v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8"/><rect x="3" y="5" width="18" height="4" rx="1"/><path d="M16 13h3"/>',
  key: '<circle cx="8" cy="12" r="4"/><path d="M12 12h9M18 12v3M15 12v2"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.2 3.6-6.5 8-6.5s8 2.3 8 6.5"/>',
  doc2: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>'
};

/* 站点根路径：由 nav.js 自身地址（assets/js/nav.js）反推 */
const ASSET_BASE = (function () {
  var src = (document.currentScript && document.currentScript.src) || '';
  var m = src.match(/^(.*?)assets\/js\/nav\.js(\?.*)?$/);
  if (m) return m[1];
  var base = window.location.href.replace(/[?#].*$/, '').replace(/[^/]*$/, '');
  return /\/pages\/$/.test(base) ? base.replace(/pages\/$/, '') : base;
})();

/* 顶部工具条：集群状态 / 工具入口 */
function renderTopbar() {
  return `<div class="topbar">
    <div class="topbar-right">
      <span class="top-chip"><span class="status-dot success"></span>网关实例 24/24</span>
      <span class="top-chip"><span class="status-dot warning"></span>Nacos 配置未同步 2</span>
      <a class="top-icon" href="${ASSET_BASE}pages/k8s-visual-config.html" title="K8s 部署配置">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${ICONS.settings}</svg>
      </a>
      <span class="top-avatar">OP</span>
    </div>
  </div>`;
}

/* 状态文案 -> 样式类型：已运行/已上线 绿色，其余灰色 */
function statusClass(text) {
  return /已(运行|上线)/.test(text) ? ' is-on' : ' is-off';
}

/* 侧边栏（Logo + 导航） */
function renderSidebar(activeKey) {
  const nav = NAV_GROUPS.map((group) => {
    const status = group.status
      ? `<span class="nav-group-status${statusClass(group.status)}">${group.status}</span>`
      : '';
    let title = '';
    if (group.title) {
      title = `<div class="nav-group-head">
        <div class="nav-section-title"><span>${group.title}</span>${status}</div>
      </div>`;
    }
    const items = group.items.map(item => {
      const icon = ICONS[item.icon] || ICONS.home;
      const active = item.key === activeKey ? 'active' : '';
      const itemStatus = item.status
        ? `<span class="nav-item-status${statusClass(item.status)}">${item.status}</span>`
        : '';
      /* 分组下的条目加 sub 类，缩进比分组标题更深，形成层级 */
      const sub = group.title ? ' sub' : '';
      return `<a class="nav-item ${active}${sub}" href="${ASSET_BASE}${item.href}">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${icon}</svg>
        <span>${item.label}</span>${itemStatus}
      </a>`;
    }).join('');
    return title + items;
  }).join('');

  const bottom = `<div class="sidebar-bottom">
      <div class="side-stack">
        <div class="side-stack-title">运行时</div>
        <div class="side-stack-item"><span>Spring Boot</span><b>3.2.5</b></div>
        <div class="side-stack-item"><span>Spring Cloud</span><b>2023.0.1</b></div>
        <div class="side-stack-item"><span>SCG（WebFlux）</span><b>4.1.5</b></div>
        <div class="side-stack-item"><span>JDK</span><b>17.0.11</b></div>
      </div>
    </div>`;

  return `<aside class="sidebar">
    <a class="sidebar-logo" href="${ASSET_BASE}index.html">
      <span class="logo-brand">API网关平台</span>
      <span class="logo-badge">微服务</span>
    </a>
    <div class="sidebar-scroll">
      <nav class="sidebar-nav">${nav}</nav>
      ${bottom}
    </div>
  </aside>`;
}

/* 通用初始化：注入工具条 + 侧边栏 */
window.initLayout = function (activeKey, options) {
  const opts = options || {};

  if (!opts.hideTopbar) {
    const topRoot = document.getElementById('topbar-root');
    if (topRoot) topRoot.innerHTML = renderTopbar();
  }

  const sidebarRoot = document.getElementById('sidebar-root');
  if (sidebarRoot) sidebarRoot.innerHTML = renderSidebar(activeKey);
};

/* 表格单元格：内容被省略号截断时，悬浮弹出完整内容 */
function initCellTips() {
  let tip = document.getElementById('cellTip');
  if (!tip) {
    tip = document.createElement('div');
    tip.id = 'cellTip';
    tip.className = 'cell-tip';
    document.body.appendChild(tip);
  }

  function place(e) {
    const pad = 14;
    const r = tip.getBoundingClientRect();
    let x = e.clientX + pad;
    let y = e.clientY + pad;
    if (x + r.width > window.innerWidth - 8) x = e.clientX - r.width - pad;
    if (y + r.height > window.innerHeight - 8) y = e.clientY - r.height - pad;
    tip.style.left = Math.max(8, x) + 'px';
    tip.style.top = Math.max(8, y) + 'px';
  }

  document.addEventListener('mouseover', function (e) {
    const td = e.target.closest ? e.target.closest('td') : null;
    if (!td || td.closest('.param-row') || td.querySelector('a')) {
      tip.classList.remove('show');
      return;
    }
    if (td.scrollWidth <= td.clientWidth + 1) {   /* 未截断，不弹 */
      tip.classList.remove('show');
      return;
    }
    tip.textContent = (td.textContent || '').trim().replace(/\s+/g, ' ');
    tip.classList.add('show');
    place(e);
  });

  document.addEventListener('mousemove', function (e) {
    if (tip.classList.contains('show')) place(e);
  });

  document.addEventListener('mouseout', function (e) {
    if (!e.relatedTarget || !e.relatedTarget.closest || !e.relatedTarget.closest('td')) {
      tip.classList.remove('show');
    }
  });

  window.addEventListener('scroll', function () { tip.classList.remove('show'); }, true);
}

/* 右侧抽屉：打开 / 关闭（点击记录行展示完整详情） */
function ensureDrawer() {
  let mask = document.getElementById('drawerMask');
  if (mask) return mask;
  mask = document.createElement('div');
  mask.className = 'drawer-mask';
  mask.id = 'drawerMask';

  const drawer = document.createElement('aside');
  drawer.className = 'drawer';
  drawer.id = 'recordDrawer';
  drawer.innerHTML =
    '<div class="drawer-header">' +
      '<div class="drawer-title" id="drawerTitle"></div>' +
      '<button class="drawer-close" id="drawerClose" type="button">\u2715</button>' +
    '</div>' +
    '<div class="drawer-body" id="drawerBody"></div>';

  document.body.appendChild(mask);
  document.body.appendChild(drawer);

  mask.addEventListener('click', closeDrawer);
  drawer.querySelector('#drawerClose').addEventListener('click', closeDrawer);
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeDrawer();
  });
  return mask;
}

function openDrawer(title, html, focusId) {
  const mask = ensureDrawer();
  document.getElementById('drawerTitle').textContent = title;
  document.getElementById('drawerBody').innerHTML = html;
  mask.classList.add('show');
  document.getElementById('recordDrawer').classList.add('show');
  document.body.style.overflow = 'hidden';
  if (focusId) {
    const el = document.getElementById(focusId);
    if (el) el.scrollIntoView({ block: 'start' });
  }
}

function closeDrawer() {
  const mask = document.getElementById('drawerMask');
  const drawer = document.getElementById('recordDrawer');
  if (!mask || !drawer) return;
  mask.classList.remove('show');
  drawer.classList.remove('show');
  document.body.style.overflow = '';
}

/* 列表行高自适应：按可用高度均分，铺满页面且不溢出（34~56px 之间） */
function fitTableRows() {
  const wrap = document.querySelector('.card-body.no-pad .table-wrap');
  if (!wrap) return;
  const table = wrap.querySelector('table');
  if (!table) return;
  const thead = table.querySelector('thead');
  const rows = Array.prototype.filter.call(table.querySelectorAll('tbody tr'), function (tr) {
    return tr.style.display !== 'none';
  });
  if (!rows.length) return;
  rows.forEach(function (tr) { tr.style.height = ''; });
  const avail = wrap.clientHeight - (thead ? thead.offsetHeight : 0);
  if (avail <= 0) return;
  const h = Math.max(34, Math.min(56, Math.floor(avail / rows.length)));
  rows.forEach(function (tr) { tr.style.height = h + 'px'; });
}

window.fitTableRows = fitTableRows;

let fitTimer = null;
window.addEventListener('resize', function () {
  clearTimeout(fitTimer);
  fitTimer = setTimeout(fitTableRows, 120);
});

/* 字节数格式化：1024 起同时给出 KB / MB */
function fmtBytes(n) {
  const v = Number(n) || 0;
  if (v < 1024) return v + ' B';
  const kb = v / 1024;
  if (kb < 1024) return v.toLocaleString() + ' B（' + kb.toFixed(2) + ' KB）';
  return v.toLocaleString() + ' B（' + (kb / 1024).toFixed(2) + ' MB）';
}
window.fmtBytes = fmtBytes;
