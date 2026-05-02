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

// Simple markdown renderer (no external dependency)
function renderMarkdown(text) {
  if (!text) return '';
  let html = escapeHtml(text);

  // Code blocks
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_, lang, code) => {
    return `<pre><code>${code}</code></pre>`;
  });

  // Inline code
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

  // Bold
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

  // Italic
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');

  // Headers
  html = html.replace(/^#### (.+)$/gm, '<h4>$1</h4>');
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');

  // Lists
  html = html.replace(/^(\s*)[-*] (.+)$/gm, '$1<li>$2</li>');
  html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');

  // Links
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');

  // Line breaks
  html = html.replace(/\n/g, '<br>');

  return html;
}

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

  // Track collapsed groups
  if (!window._collapsedGroups) window._collapsedGroups = new Set();

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
    });
  });
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

  // Keyboard shortcuts
  document.addEventListener('keydown', e => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'f') {
      if (viewer.style.display !== 'none') {
        e.preventDefault();
        viewerSearch.focus();
      }
    }
    if (e.key === 'Escape') {
      if (settingsModal.style.display !== 'none') closeSettings();
    }
  });
}

async function loadConversation(conv) {
  currentConversation = conv;
  allMessages = [];
  sessionMeta = null;
  currentFilter = 'all';
  viewerSearchQuery = '';

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

    if (block.type === 'tool_use') {
      const input = JSON.stringify(block.input || {}, null, 2);
      const plainText = `Tool: ${block.name}\nID: ${block.id}\n\n${input}`;
      allMessages.push({
        role: 'tool', kind: 'tool_use', kindLabel: 'tool use', timestamp,
        plainText, searchableText: `${block.name} ${input}`,
        html: `<div class="tool-title">Tool: ${escapeHtml(block.name || 'unknown')}</div>
               <div class="kv-grid"><div class="kv-key">id</div><div class="kv-value">${escapeHtml(block.id || '')}</div></div>
               <pre><code>${escapeHtml(input)}</code></pre>`
      });
      stats.tool++;
      return;
    }

    if (block.type === 'tool_result') {
      const resultText = stringifyContent(block.content);
      allMessages.push({
        role: 'tool', kind: 'tool_result', kindLabel: 'tool result', timestamp,
        plainText: resultText, searchableText: `${block.tool_use_id} ${resultText}`,
        html: `<div class="tool-title">Tool Result</div>
               <div class="kv-grid"><div class="kv-key">id</div><div class="kv-value">${escapeHtml(block.tool_use_id || '')}</div></div>
               <pre><code>${escapeHtml(resultText)}</code></pre>`
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
  const stats = { user: 0, assistant: 0, totalTokens: 0 };

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

        const text = contentArr[0].text || '';
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

  // Group consecutive tool/system messages
  const groups = [];
  let i = 0;
  while (i < filtered.length) {
    const msg = filtered[i];
    const isCompact = msg.role === 'tool' || msg.role === 'system';

    if (isCompact) {
      const batch = [];
      while (i < filtered.length && (filtered[i].role === 'tool' || filtered[i].role === 'system')) {
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
            ${msg.kind && msg.kind !== 'message' ? `<span class="message-kind">${escapeHtml(msg.kind)}</span>` : ''}
            ${time ? `<span class="message-time">${time}</span>` : ''}
          </div>
          <div class="message-content ${needsCollapse ? 'collapsed' : ''}">
            ${msg.html}
          </div>
          ${needsCollapse ? '<button class="expand-btn" onclick="expandMsg(this)">展开全文</button>' : ''}
        </div>`;
    }

    // Group of consecutive tool/system messages
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
            ${msg.kind && msg.kind !== 'message' ? `<span class="message-kind">${escapeHtml(msg.kind)}</span>` : ''}
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

// Start
init();
