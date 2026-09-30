// State
let conversations = [];
let currentConversation = null;
let allMessages = [];
let sessionMeta = null;
let currentFilter = 'all';
let sidebarSearchQuery = '';
let viewerSearchQuery = '';
let currentSource = 'all';
let settings = null;

// DOM
const searchInput = document.getElementById('searchInput');
const conversationList = document.getElementById('conversationList');
const welcomeScreen = document.getElementById('welcomeScreen');
const viewer = document.getElementById('viewer');
const viewerMeta = document.getElementById('viewerMeta');
const viewerStats = document.getElementById('viewerStats');
const messagesContainer = document.getElementById('messagesContainer');
const viewerSearch = document.getElementById('viewerSearch');
const settingsModal = document.getElementById('settingsModal');
const convCount = document.getElementById('convCount');

// Lightweight syntax highlighter for code blocks
function highlightCode(code, lang) {
  if (!code) return '';
  const escaped = escapeHtml(code);
  if (!lang) return escaped;
  const l = lang.toLowerCase();

  const supported = ['js', 'javascript', 'ts', 'typescript', 'json', 'py', 'python', 'sh', 'bash', 'zsh', 'html', 'css', 'sql', 'yaml', 'yml'];
  if (!supported.includes(l)) return escaped;

  const placeholders = [];
  const mask = (val, cls) => {
    const idx = placeholders.length;
    placeholders.push(`<span class="${cls}">${val}</span>`);
    return `\x00MASK_${idx}\x00`;
  };

  let text = escaped;

  // 1. Comments
  if (['py', 'python', 'sh', 'bash', 'zsh', 'yaml', 'yml'].includes(l)) {
    text = text.replace(/(#[^\n]*)/g, m => mask(m, 'token-comment'));
  } else {
    text = text.replace(/(\/\/[^\n]*|\/\*[\s\S]*?\*\/)/g, m => mask(m, 'token-comment'));
  }

  // 2. Strings
  text = text.replace(/(&quot;.*?&quot;|&#39;.*?&#39;|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)/g, m => mask(m, 'token-string'));

  // 3. Keywords
  const kwRegex = /\b(const|let|var|function|return|if|else|for|while|import|export|from|class|extends|async|await|try|catch|finally|throw|new|typeof|def|self|in|is|elif|lambda|pass|print|echo|local|select|where|insert|update|delete|table)\b/g;
  text = text.replace(kwRegex, '<span class="token-keyword">$1</span>');

  // 4. Booleans / Null
  text = text.replace(/\b(true|false|null|undefined|True|False|None)\b/g, '<span class="token-boolean">$1</span>');

  // 5. Numbers
  text = text.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="token-number">$1</span>');

  // Restore placeholders
  text = text.replace(/\x00MASK_(\d+)\x00/g, (_, i) => placeholders[+i]);

  return text;
}

// Enhanced markdown renderer (supports code blocks with copy & syntax, tables, quotes, strikethrough, lists)
function renderMarkdown(text) {
  if (!text) return '';

  const codeBlocks = [];
  // 1. Extract fenced code blocks first to protect code content from being altered by markdown rules
  let s = text.replace(/```([a-zA-Z0-9_+-]*)\r?\n([\s\S]*?)```/g, (_, lang, code) => {
    const placeholder = `\x00CODEBLOCK_${codeBlocks.length}\x00`;
    codeBlocks.push({ lang: (lang || '').trim(), code });
    return placeholder;
  });

  // 2. Extract inline code
  const inlineCodes = [];
  s = s.replace(/`([^`\n]+)`/g, (_, code) => {
    const placeholder = `\x00INLINECODE_${inlineCodes.length}\x00`;
    inlineCodes.push(code);
    return placeholder;
  });

  // 3. Escape HTML for the rest of markdown text
  s = escapeHtml(s);

  // 4. Tables (GFM syntax)
  s = s.replace(/^(\|([^\n]+)\|[ \t]*\r?\n)(\|[-:|\s]+?\|[ \t]*\r?\n)((?:\|[^\n]+\|[ \t]*(?:\r?\n|$))+)/gm, (match, headerLine, _hc, alignLine, bodyLines) => {
    const parseCells = (row) => row.trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim());
    const headers = parseCells(headerLine);
    const ths = headers.map(h => `<th>${h}</th>`).join('');
    const bodyRows = bodyLines.trim().split('\n').filter(Boolean);
    const trs = bodyRows.map(row => {
      const cells = parseCells(row);
      return `<tr>` + cells.map(c => `<td>${c}</td>`).join('') + `</tr>`;
    }).join('');
    return `<div class="table-wrapper"><table><thead><tr>${ths}</tr></thead><tbody>${trs}</tbody></table></div>`;
  });

  // 5. Blockquotes (escaped > is &gt;)
  s = s.replace(/^(?:&gt;\s?([^\n]+)\r?\n?)+/gm, (match) => {
    const inner = match.split('\n').filter(Boolean).map(line => line.replace(/^&gt;\s?/, '')).join('<br>');
    return `<blockquote>${inner}</blockquote>`;
  });

  // 6. Horizontal Rules
  s = s.replace(/^(?:---|\*\*\*|___)\s*$/gm, '<hr>');

  // 7. Strikethrough
  s = s.replace(/~~(.+?)~~/g, '<del>$1</del>');

  // 8. Bold & Italic
  s = s.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/\*(.+?)\*/g, '<em>$1</em>');

  // 9. Headers
  s = s.replace(/^#### (.+)$/gm, '<h4>$1</h4>');
  s = s.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  s = s.replace(/^## (.+)$/gm, '<h2>$1</h2>');
  s = s.replace(/^# (.+)$/gm, '<h1>$1</h1>');

  // 10. Lists
  s = s.replace(/^(\s*)[-*+] (.+)$/gm, '$1<li>$2</li>');
  s = s.replace(/^(\s*)(\d+)\. (.+)$/gm, '$1<li>$3</li>');
  s = s.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');

  // 11. Links
  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');

  // 12. Convert newlines to <br> for plain markdown paragraphs
  s = s.replace(/\n/g, '<br>');

  // 13. Reinsert inline code
  s = s.replace(/\x00INLINECODE_(\d+)\x00/g, (_, idx) => {
    const code = inlineCodes[+idx] || '';
    return `<code class="inline-code">${escapeHtml(code)}</code>`;
  });

  // 14. Reinsert fenced code blocks with language tag, syntax highlighting, and copy button
  s = s.replace(/\x00CODEBLOCK_(\d+)\x00/g, (_, idx) => {
    const item = codeBlocks[+idx] || { lang: '', code: '' };
    const lang = item.lang;
    const langLabel = lang ? `<span class="code-lang">${escapeHtml(lang)}</span>` : '<span class="code-lang">TEXT</span>';
    const langAttr = lang ? ` data-lang="${escapeHtml(lang)}"` : '';
    const highlighted = highlightCode(item.code, lang);
    return `<div class="code-block-wrapper">` +
      `<div class="code-block-header">${langLabel}<button class="copy-code-btn" onclick="copyCode(this)" title="复制代码"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> <span>复制</span></button></div>` +
      `<pre${langAttr}><code class="language-${lang || 'text'}">${highlighted}</code></pre></div>`;
  });

  return s;
}

window.copyCode = function(btn) {
  const wrapper = btn.closest('.code-block-wrapper');
  const codeEl = wrapper ? wrapper.querySelector('code') : null;
  if (!codeEl) return;
  const rawText = codeEl.textContent || '';
  navigator.clipboard.writeText(rawText).then(() => {
    const span = btn.querySelector('span');
    if (span) span.textContent = '已复制';
    btn.classList.add('copied');
    setTimeout(() => {
      if (span) span.textContent = '复制';
      btn.classList.remove('copied');
    }, 2000);
  });
};

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function formatFileSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function formatDate(isoString) {
  const d = new Date(isoString);
  const now = new Date();
  const diff = now - d;
  const dayMs = 86400000;

  if (diff < dayMs && d.getDate() === now.getDate()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  if (diff < 7 * dayMs) {
    const days = ['日', '一', '二', '三', '四', '五', '六'];
    return `周${days[d.getDay()]}`;
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

function formatFullDate(isoString) {
  return new Date(isoString).toLocaleString('zh-CN');
}

// Initialize
async function init() {
  settings = await window.api.getSettings();
  if (settings.theme === 'light') document.body.classList.add('light');
  await refreshConversations();
  setupEventListeners();
}

async function refreshConversations() {
  conversationList.innerHTML = '<div class="loading-state"><div class="spinner"></div><p>正在扫描对话记录...</p></div>';
  conversations = await window.api.scanConversations();
  renderConversationList();
}

function renderConversationList() {
  let filtered = conversations;

  if (currentSource !== 'all') {
    filtered = filtered.filter(c => c.source === currentSource);
  }

  if (sidebarSearchQuery) {
    const q = sidebarSearchQuery.toLowerCase();
    filtered = filtered.filter(c =>
      c.fileName.toLowerCase().includes(q) ||
      c.parentDir.toLowerCase().includes(q) ||
      c.id.toLowerCase().includes(q) ||
      (c.title || '').toLowerCase().includes(q) ||
      (c.project || '').toLowerCase().includes(q)
    );
  }

  convCount.textContent = `${filtered.length} 条对话`;

  if (!filtered.length) {
    conversationList.innerHTML = '<div class="empty-state">没有找到对话记录</div>';
    return;
  }

  // Group by project
  const groups = new Map();
  for (const conv of filtered) {
    const key = conv.project || 'default';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(conv);
  }

  // Sort groups: named projects first (by count), then default
  const sortedGroups = [...groups.entries()].sort(([aKey, aVal], [bKey, bVal]) => {
    if (aKey === 'default') return 1;
    if (bKey === 'default') return -1;
    return bVal.length - aVal.length;
  });

  window._currentSortedGroups = sortedGroups;

  // Update projects count
  const projectCount = document.getElementById('projectCount');
  if (projectCount) {
    projectCount.textContent = `${sortedGroups.length} 个项目`;
  }

  // Track collapsed groups
  if (!window._collapsedGroups) window._collapsedGroups = new Set();

  const allCollapsed = sortedGroups.length > 0 && sortedGroups.every(([proj]) => window._collapsedGroups.has(proj));
  updateProjectsToggleButtons(allCollapsed);

  let html = '';
  for (const [project, convs] of sortedGroups) {
    const isCollapsed = window._collapsedGroups.has(project);
    const label = project === 'default' ? 'Default' : project;

    html += `<div class="conv-group">
      <div class="conv-group-header" data-group="${escapeHtml(project)}">
        <svg class="conv-group-arrow ${isCollapsed ? 'collapsed' : ''}" width="10" height="10" viewBox="0 0 10 10"><path d="M3 1l4 4-4 4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
        <span class="conv-group-name">${escapeHtml(label)}</span>
        <span class="conv-group-count">${convs.length}</span>
      </div>
      <div class="conv-group-items" ${isCollapsed ? 'style="display:none"' : ''}>
        ${convs.map(conv => {
          const isActive = currentConversation?.id === conv.id && currentConversation?.source === conv.source;
          const sourceLabel = conv.source === 'cc' ? 'CC' : 'Codex';
          const title = conv.title || conv.parentDir || conv.id.slice(0, 12);

          return `
            <div class="conv-item ${isActive ? 'active' : ''}"
                 data-id="${conv.id}" data-source="${conv.source}"
                 data-path="${escapeHtml(conv.filePath)}">
              <div class="conv-item-header">
                <span class="conv-source ${conv.source}">${sourceLabel}</span>
                <span class="conv-time">${formatDate(conv.mtime)}</span>
              </div>
              <div class="conv-title">${escapeHtml(title)}</div>
              <div class="conv-meta">${escapeHtml(conv.fileName)} · ${formatFileSize(conv.fileSize)}</div>
            </div>`;
        }).join('')}
      </div>
    </div>`;
  }

  conversationList.innerHTML = html;

  // Bind group header click
  conversationList.querySelectorAll('.conv-group-header').forEach(header => {
    header.addEventListener('click', () => {
      const group = header.dataset.group;
      const items = header.nextElementSibling;
      const arrow = header.querySelector('.conv-group-arrow');
      if (window._collapsedGroups.has(group)) {
        window._collapsedGroups.delete(group);
        items.style.display = '';
        arrow.classList.remove('collapsed');
      } else {
        window._collapsedGroups.add(group);
        items.style.display = 'none';
        arrow.classList.add('collapsed');
      }
      const isAllCollapsed = window._currentSortedGroups.length > 0 && window._currentSortedGroups.every(([proj]) => window._collapsedGroups.has(proj));
      updateProjectsToggleButtons(isAllCollapsed);
    });
  });
}

function updateProjectsToggleButtons(allCollapsed) {
  const btn = document.getElementById('btnToggleAllProjects');
  if (btn) {
    const text = btn.querySelector('.toggle-text');
    if (text) text.textContent = allCollapsed ? '展开全部' : '折叠全部';
    btn.setAttribute('title', allCollapsed ? '一键展开所有项目 (Alt+C)' : '一键折叠所有项目 (Alt+C)');
    btn.classList.toggle('active', allCollapsed);
  }
  const headerBtn = document.getElementById('btnToggleAllProjectsHeader');
  if (headerBtn) {
    headerBtn.setAttribute('title', allCollapsed ? '一键展开所有项目 (Alt+C)' : '一键折叠所有项目 (Alt+C)');
    headerBtn.classList.toggle('active', allCollapsed);
  }
}

function toggleAllProjects() {
  if (!window._currentSortedGroups || !window._currentSortedGroups.length) return;
  const allCollapsed = window._currentSortedGroups.every(([proj]) => window._collapsedGroups.has(proj));
  if (allCollapsed) {
    window._collapsedGroups.clear();
  } else {
    window._currentSortedGroups.forEach(([proj]) => window._collapsedGroups.add(proj));
  }
  renderConversationList();
}

// path.basename polyfill for renderer
const path = {
  basename(p) {
    const parts = p.replace(/\\/g, '/').split('/');
    return parts[parts.length - 1] || p;
  }
};

function setupEventListeners() {
  // Theme toggle
  document.getElementById('btnTheme').addEventListener('click', async () => {
    document.body.classList.toggle('light');
    settings.theme = document.body.classList.contains('light') ? 'light' : 'dark';
    await window.api.saveSettings(settings);
  });

  // Source tabs
  document.querySelectorAll('.source-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.source-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      currentSource = tab.dataset.source;
      renderConversationList();
    });
  });

  // Search
  searchInput.addEventListener('input', e => {
    sidebarSearchQuery = e.target.value;
    renderConversationList();
  });

  // Conversation click
  conversationList.addEventListener('click', e => {
    const item = e.target.closest('.conv-item');
    if (!item) return;
    const convId = item.dataset.id;
    const convSource = item.dataset.source;
    const convPath = item.dataset.path;
    const conv = conversations.find(c => c.id === convId && c.source === convSource);
    if (conv) loadConversation(conv);
  });

  // Refresh
  document.getElementById('btnRefresh').addEventListener('click', refreshConversations);

  // Settings
  document.getElementById('btnSettings').addEventListener('click', openSettings);
  document.getElementById('btnCloseSettings').addEventListener('click', closeSettings);
  document.getElementById('btnCancelSettings').addEventListener('click', closeSettings);
  document.getElementById('btnSaveSettings').addEventListener('click', saveSettingsHandler);

  // Add path buttons
  document.querySelectorAll('.add-path-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const dir = await window.api.pickDirectory();
      if (dir) addPathEntry(btn.dataset.target, dir);
    });
  });

  // Filter buttons
  document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentFilter = btn.dataset.filter;
      renderMessages();
    });
  });

  // Viewer search
  viewerSearch.addEventListener('input', e => {
    viewerSearchQuery = e.target.value;
    renderMessages();
  });

  // Export buttons
  document.getElementById('btnExportMd').addEventListener('click', exportMarkdown);
  document.getElementById('btnExportHtml').addEventListener('click', exportHtml);
  document.getElementById('btnOpenFinder').addEventListener('click', () => {
    if (currentConversation) window.api.openInFinder(currentConversation.filePath);
  });

  // Toggle all tools button
  const btnToggleAllTools = document.getElementById('btnToggleAllTools');
  if (btnToggleAllTools) {
    btnToggleAllTools.addEventListener('click', toggleAllTools);
  }

  // Toggle all projects buttons
  const btnToggleAllProjects = document.getElementById('btnToggleAllProjects');
  if (btnToggleAllProjects) {
    btnToggleAllProjects.addEventListener('click', toggleAllProjects);
  }
  const btnToggleAllProjectsHeader = document.getElementById('btnToggleAllProjectsHeader');
  if (btnToggleAllProjectsHeader) {
    btnToggleAllProjectsHeader.addEventListener('click', toggleAllProjects);
  }

  // Keyboard shortcuts
  document.addEventListener('keydown', e => {
    const isEditing = ['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName);

    // Alt+C / Option+C: Toggle all projects collapse/expand
    if (e.altKey && (e.key === 'c' || e.key === 'C' || e.code === 'KeyC')) {
      e.preventDefault();
      toggleAllProjects();
      return;
    }

    // Cmd+F / Ctrl+F: Focus viewer search (or if viewer not open, focus sidebar search)
    if ((e.metaKey || e.ctrlKey) && (e.key === 'f' || e.key === 'F')) {
      e.preventDefault();
      if (e.shiftKey) {
        searchInput.focus();
        searchInput.select();
      } else if (viewer.style.display !== 'none') {
        viewerSearch.focus();
        viewerSearch.select();
      } else {
        searchInput.focus();
        searchInput.select();
      }
      return;
    }

    // Slash '/' outside input focuses viewer search
    if (!isEditing && e.key === '/') {
      if (viewer.style.display !== 'none') {
        e.preventDefault();
        viewerSearch.focus();
        viewerSearch.select();
        return;
      }
    }

    // Escape: Close modal or blur search
    if (e.key === 'Escape') {
      if (settingsModal.style.display !== 'none') {
        closeSettings();
        return;
      }
      if (isEditing) {
        document.activeElement.blur();
        return;
      }
    }

    // Up / Down / j / k to navigate conversation list when not editing
    if (!isEditing && (e.key === 'ArrowDown' || e.key === 'j' || e.key === 'ArrowUp' || e.key === 'k')) {
      const items = Array.from(conversationList.querySelectorAll('.conv-item'));
      if (!items.length) return;
      e.preventDefault();
      const currentIndex = items.findIndex(item => item.classList.contains('active'));
      let nextIndex = 0;
      if (e.key === 'ArrowDown' || e.key === 'j') {
        nextIndex = currentIndex < items.length - 1 ? currentIndex + 1 : 0;
      } else {
        nextIndex = currentIndex > 0 ? currentIndex - 1 : items.length - 1;
      }
      const nextItem = items[nextIndex];
      if (nextItem) {
        nextItem.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        const convId = nextItem.dataset.id;
        const convSource = nextItem.dataset.source;
        const conv = conversations.find(c => c.id === convId && c.source === convSource);
        if (conv) loadConversation(conv);
      }
    }
  });
}

let allToolsExpanded = false;
function toggleAllTools() {
  const groups = document.querySelectorAll('.message-group');
  if (!groups.length) return;
  allToolsExpanded = !allToolsExpanded;
  groups.forEach(group => {
    const items = group.querySelector('.message-group-items');
    const arrow = group.querySelector('.message-group-header .compact-arrow');
    if (allToolsExpanded) {
      items?.classList.remove('compact-hidden');
      arrow?.classList.add('rotated');
      group.querySelectorAll('.group-item').forEach(item => {
        item.querySelector('.group-item-content')?.classList.remove('compact-hidden');
        item.querySelector('.compact-arrow')?.classList.add('rotated');
      });
    } else {
      items?.classList.add('compact-hidden');
      arrow?.classList.remove('rotated');
      group.querySelectorAll('.group-item').forEach(item => {
        item.querySelector('.group-item-content')?.classList.add('compact-hidden');
        item.querySelector('.compact-arrow')?.classList.remove('rotated');
      });
    }
  });
  const btn = document.getElementById('btnToggleAllTools');
  if (btn) {
    const span = btn.querySelector('span');
    if (span) span.textContent = allToolsExpanded ? '折叠全部工具' : '展开全部工具';
    btn.classList.toggle('active', allToolsExpanded);
  }
}

async function loadConversation(conv) {
  currentConversation = conv;
  allMessages = [];
  sessionMeta = null;
  currentFilter = 'all';
  viewerSearchQuery = '';
  allToolsExpanded = false;
  const toggleBtn = document.getElementById('btnToggleAllTools');
  if (toggleBtn) {
    const span = toggleBtn.querySelector('span');
    if (span) span.textContent = '展开全部工具';
    toggleBtn.classList.remove('active');
  }

  // Update active state
  document.querySelectorAll('.conv-item').forEach(item => {
    item.classList.toggle('active',
      item.dataset.id === conv.id && item.dataset.source === conv.source);
  });

  welcomeScreen.style.display = 'none';
  viewer.style.display = 'flex';
  messagesContainer.innerHTML = '<div class="loading-state"><div class="spinner"></div><p>正在加载对话...</p></div>';

  const content = await window.api.readConversation(conv.filePath);
  if (conv.source === 'cc') {
    parseCCJSONL(content);
  } else {
    parseCodexJSONL(content);
  }

  updateViewerHeader(conv);
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  document.querySelector('.filter-btn[data-filter="all"]').classList.add('active');
  viewerSearch.value = '';
  renderMessages();
}

// --- Claude Code JSONL Parser ---
function parseCCJSONL(content) {
  const lines = content.split('\n').filter(Boolean);
  const stats = { user: 0, assistant: 0, tool: 0, totalTokens: 0 };

  lines.forEach((line, index) => {
    try {
      const record = JSON.parse(line);
      ingestCCRecord(record, stats);
    } catch {
      allMessages.push({
        role: 'system', kind: 'parse_error', kindLabel: 'Parse Error',
        timestamp: null, plainText: line, searchableText: line,
        html: `<pre>${escapeHtml(`Line ${index + 1} parse error:\n${line}`)}</pre>`
      });
    }
  });

  allMessages._stats = stats;
}

function ingestCCRecord(record, stats) {
  const { type, timestamp } = record;

  if (!sessionMeta) {
    sessionMeta = {
      sessionId: record.sessionId || '', cwd: record.cwd || '',
      gitBranch: record.gitBranch || '', version: record.version || '',
      permissionMode: record.permissionMode || ''
    };
  } else {
    sessionMeta.sessionId ||= record.sessionId || '';
    sessionMeta.cwd ||= record.cwd || '';
    sessionMeta.gitBranch ||= record.gitBranch || '';
    sessionMeta.version ||= record.version || '';
    sessionMeta.permissionMode ||= record.permissionMode || '';
  }

  if (type === 'user' || type === 'assistant') {
    ingestCCMessage(record, stats);
    return;
  }

  if (type === 'system') {
    allMessages.push({
      role: 'system', kind: record.subtype || 'system', kindLabel: record.subtype || 'system',
      timestamp, plainText: JSON.stringify(record, null, 2), searchableText: JSON.stringify(record),
      html: `<pre>${escapeHtml(JSON.stringify(record, null, 2))}</pre>`
    });
    return;
  }

  if (type === 'attachment') {
    const att = record.attachment || {};
    const text = att.content || JSON.stringify(att, null, 2);
    allMessages.push({
      role: 'attachment', kind: att.type || 'attachment', kindLabel: att.type || 'attachment',
      timestamp, plainText: text, searchableText: text, html: renderMarkdown(text)
    });
  }
}

function ingestCCMessage(record, stats) {
  const role = record.type;
  const message = record.message || {};
  const content = message.content;
  const timestamp = record.timestamp;
  const usage = message.usage || {};

  if (role === 'assistant') stats.totalTokens += usage.output_tokens || 0;

  if (typeof content === 'string') {
    const text = content.trim();
    if (!text) return;
    allMessages.push(buildMsg(role, 'message', text, timestamp));
    stats[role]++;
    return;
  }

  if (!Array.isArray(content)) return;

  content.forEach(block => {
    if (!block || typeof block !== 'object') return;

    if (block.type === 'text') {
      const text = (block.text || '').trim();
      if (!text) return;
      allMessages.push(buildMsg(role, 'message', text, timestamp));
      stats[role]++;
      return;
    }

    if (block.type === 'thinking') {
      const text = (block.thinking || '').trim();
      if (!text) return;
      allMessages.push({
        role: 'assistant',
        kind: 'thinking',
        kindLabel: '思考过程',
        timestamp,
        plainText: text,
        searchableText: text,
        html: `<div class="thinking-block">
                 <div class="thinking-header" onclick="toggleThinking(this)">
                   <svg class="thinking-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a7 7 0 0 0-7 7c0 2.38 1.19 4.47 3 5.74V17a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2v-2.26c1.81-1.27 3-3.36 3-5.74a7 7 0 0 0-7-7z"/><path d="M9 21h6"/></svg>
                   <span class="thinking-title">思考过程 (Thinking)</span>
                   <span class="thinking-badge">${text.length} 字符</span>
                   <svg class="compact-arrow" width="10" height="10" viewBox="0 0 10 10"><path d="M3 1l4 4-4 4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
                 </div>
                 <div class="thinking-content compact-hidden">${renderMarkdown(text)}</div>
               </div>`
      });
      return;
    }

    if (block.type === 'redacted_thinking') {
      allMessages.push({
        role: 'assistant',
        kind: 'thinking',
        kindLabel: '思考过程 (已省略)',
        timestamp,
        plainText: '[思考过程已被模型提供方省略]',
        searchableText: '思考过程 已省略',
        html: `<div class="thinking-block redacted">
                 <div class="thinking-header disabled">
                   <span class="thinking-title">思考过程 (已由提供方省略)</span>
                 </div>
               </div>`
      });
      return;
    }

    if (block.type === 'tool_use') {
      const input = JSON.stringify(block.input || {}, null, 2);
      const plainText = `Tool: ${block.name}\nID: ${block.id}\n\n${input}`;
      allMessages.push({
        role: 'tool', kind: 'tool_use', kindLabel: `tool: ${block.name || 'call'}`, timestamp,
        plainText, searchableText: `${block.name} ${input}`,
        html: `<div class="tool-title"><span class="tool-badge">Tool</span> <strong>${escapeHtml(block.name || 'unknown')}</strong></div>
               <div class="kv-grid"><div class="kv-key">id</div><div class="kv-value">${escapeHtml(block.id || '')}</div></div>
               <div class="code-block-wrapper"><div class="code-block-header"><span class="code-lang">JSON</span><button class="copy-code-btn" onclick="copyCode(this)" title="复制代码"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> <span>复制</span></button></div><pre data-lang="json"><code class="language-json">${highlightCode(input, 'json')}</code></pre></div>`
      });
      stats.tool++;
      return;
    }

    if (block.type === 'tool_result') {
      const resultText = stringifyContent(block.content);
      allMessages.push({
        role: 'tool', kind: 'tool_result', kindLabel: 'tool result', timestamp,
        plainText: resultText, searchableText: `${block.tool_use_id} ${resultText}`,
        html: `<div class="tool-title"><span class="tool-badge">Result</span> <strong>${escapeHtml(block.tool_use_id || '')}</strong></div>
               <div class="code-block-wrapper"><div class="code-block-header"><span class="code-lang">OUTPUT</span><button class="copy-code-btn" onclick="copyCode(this)" title="复制输出"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> <span>复制</span></button></div><pre><code>${escapeHtml(resultText)}</code></pre></div>`
      });
      stats.tool++;
    }
  });
}

function stringifyContent(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) return content.map(i => typeof i === 'string' ? i : JSON.stringify(i, null, 2)).join('\n\n');
  if (content && typeof content === 'object') return JSON.stringify(content, null, 2);
  return String(content ?? '');
}

function buildMsg(role, kind, text, timestamp) {
  return { role, kind, timestamp, plainText: text, searchableText: text, html: renderMarkdown(text) };
}

// --- Codex JSONL Parser ---
function parseCodexJSONL(content) {
  const lines = content.trim().split('\n');
  const stats = { user: 0, assistant: 0, tool: 0, totalTokens: 0 };

  lines.forEach((line, index) => {
    try {
      const data = JSON.parse(line);
      const { type, payload, timestamp } = data;

      if (type === 'session_meta') {
        sessionMeta = payload;
      } else if (type === 'response_item') {
        const role = payload.role;
        const contentArr = payload.content || [];
        if (!contentArr.length) return;

        contentArr.forEach(item => {
          const text = item.text || '';
          if (!text) return;

          // Skip system/context messages
          if (role === 'developer' && (text.startsWith('<permissions') || text.startsWith('<app-context>') || text.startsWith('<collaboration_mode>'))) return;
          if (role === 'user' && (text.startsWith('<environment_context>') || text.startsWith('# AGENTS.md'))) return;

          if (role === 'user') {
            const isReal = !text.startsWith('<') && !text.startsWith('# AGENTS') && text.length > 0;
            if (!isReal) return;
            allMessages.push({ role: 'user', kind: 'message', timestamp, plainText: text, searchableText: text, html: renderMarkdown(text) });
            stats.user++;
          } else if (role === 'assistant') {
            allMessages.push({ role: 'assistant', kind: 'message', timestamp, plainText: text, searchableText: text, html: renderMarkdown(text) });
            stats.assistant++;
          } else if (role === 'developer' && !text.startsWith('<')) {
            allMessages.push({ role: 'system', kind: 'developer', timestamp, plainText: text, searchableText: text, html: renderMarkdown(text) });
          }
        });
      } else if (type === 'event_msg') {
        if (payload.type === 'token_count') {
          stats.totalTokens += payload.input_tokens || 0;
        }
      }
    } catch {}
  });

  allMessages._stats = stats;
}

// --- Rendering ---
function updateViewerHeader(conv) {
  const sourceLabel = conv.source === 'cc' ? 'Claude Code' : 'Codex';
  let metaHtml = `<strong>${sourceLabel}</strong> · ${escapeHtml(conv.fileName)}`;

  if (sessionMeta) {
    if (sessionMeta.cwd) metaHtml += ` · ${escapeHtml(sessionMeta.cwd)}`;
    if (sessionMeta.gitBranch) metaHtml += ` · ${escapeHtml(sessionMeta.gitBranch)}`;
    if (sessionMeta.model_provider) metaHtml += ` · ${escapeHtml(sessionMeta.model_provider)}`;
  }
  viewerMeta.innerHTML = metaHtml;

  const stats = allMessages._stats || {};
  let statsHtml = '';
  if (stats.user) statsHtml += `<div class="stat-item"><span class="stat-value">${stats.user}</span><span class="stat-label">用户</span></div>`;
  if (stats.assistant) statsHtml += `<div class="stat-item"><span class="stat-value">${stats.assistant}</span><span class="stat-label">助手</span></div>`;
  if (stats.tool) statsHtml += `<div class="stat-item"><span class="stat-value">${stats.tool}</span><span class="stat-label">工具</span></div>`;
  if (stats.totalTokens) statsHtml += `<div class="stat-item"><span class="stat-value">${stats.totalTokens.toLocaleString()}</span><span class="stat-label">Tokens</span></div>`;
  viewerStats.innerHTML = statsHtml;
}

function renderMessages() {
  let filtered = allMessages;

  if (currentFilter !== 'all') {
    filtered = filtered.filter(m => m.role === currentFilter);
  }

  const sq = viewerSearchQuery.toLowerCase();
  if (sq) {
    filtered = filtered.filter(m => (m.searchableText || '').toLowerCase().includes(sq));
  }

  if (!filtered.length) {
    messagesContainer.innerHTML = '<div class="empty-state">没有匹配的消息</div>';
    return;
  }

  // Group consecutive tool/system/attachment messages
  const groups = [];
  let i = 0;
  while (i < filtered.length) {
    const msg = filtered[i];
    const isCompact = msg.role === 'tool' || msg.role === 'system' || msg.role === 'attachment';

    if (isCompact) {
      const batch = [];
      while (i < filtered.length && (filtered[i].role === 'tool' || filtered[i].role === 'system' || filtered[i].role === 'attachment')) {
        batch.push(filtered[i]);
        i++;
      }
      groups.push({ type: 'group', messages: batch });
    } else {
      groups.push({ type: 'single', message: msg });
      i++;
    }
  }

  const roleLabel = { user: 'User', assistant: 'Assistant', tool: 'Tool', system: 'System', attachment: 'Attachment', meta: 'Meta' };

  messagesContainer.innerHTML = groups.map((group, gi) => {
    if (group.type === 'single') {
      const msg = group.message;
      const time = msg.timestamp ? new Date(msg.timestamp).toLocaleString('zh-CN') : '';
      const needsCollapse = (msg.plainText || '').length > 1800;
      return `
        <div class="message ${msg.role}">
          <div class="message-header">
            <span class="message-role ${msg.role}">${roleLabel[msg.role] || msg.role}</span>
            ${msg.kind && msg.kind !== 'message' ? `<span class="message-kind">${escapeHtml(msg.kindLabel || msg.kind)}</span>` : ''}
            ${time ? `<span class="message-time">${time}</span>` : ''}
            <button class="msg-copy-btn" onclick="copyMessageText(this)" title="复制消息全文">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            </button>
          </div>
          <div class="message-content ${needsCollapse ? 'collapsed' : ''}">
            ${msg.html}
          </div>
          ${needsCollapse ? '<button class="expand-btn" onclick="expandMsg(this)">展开全文</button>' : ''}
        </div>`;
    }

    // Group of consecutive tool/system/attachment messages
    const msgs = group.messages;
    const firstTime = msgs[0].timestamp ? new Date(msgs[0].timestamp).toLocaleString('zh-CN') : '';
    const lastTime = msgs[msgs.length - 1].timestamp ? new Date(msgs[msgs.length - 1].timestamp).toLocaleString('zh-CN') : '';
    const timeRange = firstTime === lastTime ? firstTime : `${firstTime} - ${lastTime}`;

    // Count by kind
    const kindCounts = {};
    msgs.forEach(m => {
      const k = m.kind || m.role;
      kindCounts[k] = (kindCounts[k] || 0) + 1;
    });
    const kindSummary = Object.entries(kindCounts).map(([k, v]) => v > 1 ? `${v}x ${k}` : k).join(', ');

    const itemsHtml = msgs.map((msg, mi) => {
      const t = msg.timestamp ? new Date(msg.timestamp).toLocaleString('zh-CN') : '';
      return `
        <div class="group-item">
          <div class="group-item-header" onclick="toggleGroupItem(this)">
            <span class="message-role ${msg.role}">${roleLabel[msg.role] || msg.role}</span>
            ${msg.kind && msg.kind !== 'message' ? `<span class="message-kind">${escapeHtml(msg.kindLabel || msg.kind)}</span>` : ''}
            ${t ? `<span class="message-time">${t}</span>` : ''}
            <svg class="compact-arrow" width="10" height="10" viewBox="0 0 10 10"><path d="M3 1l4 4-4 4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          </div>
          <div class="group-item-content compact-hidden">${msg.html}</div>
        </div>`;
    }).join('');

    return `
      <div class="message-group" data-group-index="${gi}">
        <div class="message-group-header" onclick="toggleGroup(this)">
          <svg class="compact-arrow" width="10" height="10" viewBox="0 0 10 10"><path d="M3 1l4 4-4 4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          <span class="group-summary">${escapeHtml(kindSummary)}</span>
          <span class="group-count">${msgs.length} calls</span>
          <span class="message-time">${escapeHtml(timeRange)}</span>
        </div>
        <div class="message-group-items compact-hidden">
          ${itemsHtml}
        </div>
      </div>`;
  }).join('');

  if (sq) {
    highlightSearchKeywords(messagesContainer, viewerSearchQuery);
  }
}

function highlightSearchKeywords(container, query) {
  if (!query || !query.trim()) return;
  const q = query.trim();
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
  const nodesToReplace = [];

  while (walker.nextNode()) {
    const node = walker.currentNode;
    if (node.parentElement && ['SCRIPT', 'STYLE', 'BUTTON'].includes(node.parentElement.tagName)) continue;
    if (node.parentElement && node.parentElement.closest('.search-highlight')) continue;
    if (node.nodeValue && node.nodeValue.toLowerCase().includes(q.toLowerCase())) {
      nodesToReplace.push(node);
    }
  }

  const regex = new RegExp(`(${q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
  nodesToReplace.forEach(node => {
    const parent = node.parentNode;
    if (!parent) return;
    const frag = document.createDocumentFragment();
    const parts = node.nodeValue.split(regex);
    parts.forEach(part => {
      if (part.toLowerCase() === q.toLowerCase()) {
        const mark = document.createElement('mark');
        mark.className = 'search-highlight';
        mark.textContent = part;
        frag.appendChild(mark);
      } else if (part) {
        frag.appendChild(document.createTextNode(part));
      }
    });
    parent.replaceChild(frag, node);
  });
}

window.expandMsg = function(btn) {
  btn.previousElementSibling.classList.remove('collapsed');
  btn.style.display = 'none';
};

window.toggleGroup = function(header) {
  const group = header.closest('.message-group');
  const items = group.querySelector('.message-group-items');
  const arrow = header.querySelector('.compact-arrow');
  items.classList.toggle('compact-hidden');
  arrow.classList.toggle('rotated');
};

window.toggleGroupItem = function(header) {
  const item = header.closest('.group-item');
  const content = item.querySelector('.group-item-content');
  const arrow = header.querySelector('.compact-arrow');
  content.classList.toggle('compact-hidden');
  arrow.classList.toggle('rotated');
};

window.toggleThinking = function(header) {
  const container = header.closest('.thinking-block');
  if (!container || container.classList.contains('redacted')) return;
  const content = container.querySelector('.thinking-content');
  const arrow = header.querySelector('.compact-arrow');
  content?.classList.toggle('compact-hidden');
  arrow?.classList.toggle('rotated');
};

// --- Settings ---
function openSettings() {
  settingsModal.style.display = 'flex';
  renderPathList('ccPaths', settings.ccPaths || []);
  renderPathList('codexPaths', settings.codexPaths || []);
}

function closeSettings() {
  settingsModal.style.display = 'none';
}

function renderPathList(containerId, paths) {
  const container = document.getElementById(containerId);
  container.innerHTML = paths.map((p, i) => `
    <div class="path-entry">
      <input type="text" value="${escapeHtml(p)}" data-index="${i}" class="path-input">
      <button class="path-remove" data-index="${i}">&times;</button>
    </div>
  `).join('');

  container.querySelectorAll('.path-remove').forEach(btn => {
    btn.addEventListener('click', () => {
      paths.splice(parseInt(btn.dataset.index), 1);
      renderPathList(containerId, paths);
    });
  });
}

function addPathEntry(target, dirPath) {
  const key = target === 'cc' ? 'ccPaths' : 'codexPaths';
  if (!settings[key]) settings[key] = [];
  settings[key].push(dirPath);
  renderPathList(key, settings[key]);
}

async function saveSettingsHandler() {
  // Read current values from inputs
  const ccInputs = document.querySelectorAll('#ccPaths .path-input');
  const codexInputs = document.querySelectorAll('#codexPaths .path-input');

  settings.ccPaths = Array.from(ccInputs).map(i => i.value).filter(Boolean);
  settings.codexPaths = Array.from(codexInputs).map(i => i.value).filter(Boolean);

  await window.api.saveSettings(settings);
  closeSettings();
  await refreshConversations();
}

// --- Export ---
function exportMarkdown() {
  if (!allMessages.length) return;
  const lines = ['# Chat Record', ''];
  if (sessionMeta) {
    if (sessionMeta.sessionId) lines.push(`- Session: ${sessionMeta.sessionId}`);
    if (sessionMeta.cwd) lines.push(`- CWD: ${sessionMeta.cwd}`);
    if (sessionMeta.gitBranch) lines.push(`- Branch: ${sessionMeta.gitBranch}`);
    lines.push('');
  }
  allMessages.forEach(msg => {
    const role = { user: 'User', assistant: 'Assistant', tool: 'Tool', system: 'System' }[msg.role] || msg.role;
    const time = msg.timestamp ? ` (${new Date(msg.timestamp).toLocaleString('zh-CN')})` : '';
    lines.push(`## ${role}${time}`, '', msg.plainText || '', '');
  });
  downloadFile(lines.join('\n'), `chat-${(sessionMeta?.sessionId || 'export').slice(0, 8)}.md`, 'text/markdown');
}

function exportHtml() {
  if (!allMessages.length) return;
  const msgsHtml = allMessages.map(msg => {
    const time = msg.timestamp ? new Date(msg.timestamp).toLocaleString('zh-CN') : '';
    const roleLabel = { user: 'User', assistant: 'Assistant', tool: 'Tool', system: 'System' }[msg.role] || msg.role;
    return `<div class="message ${msg.role}">
      <div class="message-header"><span class="message-role ${msg.role}">${roleLabel}</span>${time ? `<span class="message-time">${time}</span>` : ''}</div>
      <div class="message-content">${msg.html}</div>
    </div>`;
  }).join('');

  // Inline styles from the stylesheet link
  const styleEl = document.querySelector('link[rel=stylesheet]');
  let css = '';
  try {
    css = Array.from(document.styleSheets).map(s => {
      try { return Array.from(s.cssRules).map(r => r.cssText).join('\n'); } catch { return ''; }
    }).join('\n');
  } catch {}

  const fullHtml = `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>Chat Record</title><style>${css}</style></head>
    <body style="background:#0f0f1a;color:#e4e4e4;"><div class="messages-container" style="max-width:900px;margin:0 auto;padding:20px;">${msgsHtml}</div></body></html>`;
  downloadFile(fullHtml, `chat-${(sessionMeta?.sessionId || 'export').slice(0, 8)}.html`, 'text/html');
}

function downloadFile(content, filename, mimeType) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

window.copyMessageText = function(btn) {
  const msgEl = btn.closest('.message');
  if (!msgEl) return;
  const contentEl = msgEl.querySelector('.message-content');
  if (!contentEl) return;
  const text = contentEl.innerText || '';
  navigator.clipboard.writeText(text).then(() => {
    btn.classList.add('copied');
    const origTitle = btn.getAttribute('title') || '';
    btn.setAttribute('title', '已复制');
    setTimeout(() => {
      btn.classList.remove('copied');
      btn.setAttribute('title', origTitle);
    }, 2000);
  });
};

// Start
init();
