const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');

const SETTINGS_FILE = path.join(app.getPath('userData'), 'settings.json');

function loadSettings() {
  try {
    return JSON.parse(fs.readFileSync(SETTINGS_FILE, 'utf-8'));
  } catch {
    return {
      ccPaths: [path.join(os.homedir(), '.claude', 'projects')],
      codexPaths: [
        path.join(os.homedir(), '.codex', 'sessions'),
        path.join(os.homedir(), '.codex', 'archived_sessions'),
      ],
    };
  }
}

function saveSettings(settings) {
  fs.writeFileSync(SETTINGS_FILE, JSON.stringify(settings, null, 2));
}

function readSessionMeta(filePath) {
  try {
    const fd = fs.openSync(filePath, 'r');
    const buf = Buffer.alloc(65536);
    const bytesRead = fs.readSync(fd, buf, 0, 65536, 0);
    fs.closeSync(fd);
    const chunk = buf.toString('utf-8', 0, bytesRead);
    // Find session_meta with cwd without parsing the full (potentially huge) line
    const cwdMatch = chunk.match(/"type"\s*:\s*"session_meta".*?"cwd"\s*:\s*"([^"]+)"/);
    if (cwdMatch) return cwdMatch[1];
    return null;
  } catch {
    return null;
  }
}

function scanDirectory(dirPath, source) {
  const results = [];
  try {
    const entries = fs.readdirSync(dirPath, { withFileTypes: true, recursive: true });
    for (const entry of entries) {
      if (entry.isFile() && entry.name.endsWith('.jsonl')) {
        const fullPath = path.join(entry.parentPath || entry.path, entry.name);
        try {
          const stat = fs.statSync(fullPath);
          const sessionId = entry.name.replace('.jsonl', '');
          const parentDir = path.basename(entry.parentPath || entry.path);

          let title = parentDir;
          let project = '';

          if (source === 'cc') {
            title = parentDir.replace(/^-Users-[^-]+/, '~').replace(/-/g, '/');
            const parts = title.split('/').filter(Boolean);
            project = parts[parts.length - 1] || title;
          } else {
            // Codex: read session_meta.cwd from file header
            const cwd = readSessionMeta(fullPath);
            if (cwd) {
              title = cwd.replace(/^\/Users\/[^/]+/, '~');
              const parts = title.split('/').filter(Boolean);
              project = parts[parts.length - 1] || title;
            } else {
              // Fallback to date-based grouping
              const relPath = fullPath.replace(dirPath, '').replace(/^\//, '');
              const pathParts = relPath.split('/');
              if (pathParts.length >= 2) {
                project = pathParts.slice(0, 2).join('/');
              }
            }
          }

          results.push({
            id: sessionId,
            source,
            filePath: fullPath,
            fileName: entry.name,
            fileSize: stat.size,
            mtime: stat.mtime.toISOString(),
            parentDir,
            title,
            project,
          });
        } catch {}
      }
    }
  } catch {}
  return results;
}

function scanAllConversations(settings) {
  let all = [];
  for (const p of settings.ccPaths || []) {
    all = all.concat(scanDirectory(p, 'cc'));
  }
  for (const p of settings.codexPaths || []) {
    all = all.concat(scanDirectory(p, 'codex'));
  }
  all.sort((a, b) => new Date(b.mtime) - new Date(a.mtime));
  return all;
}

function readFileHead(filePath, lines = 3) {
  try {
    const content = fs.readFileSync(filePath, 'utf-8');
    const firstLines = content.split('\n').filter(Boolean).slice(0, lines);
    return firstLines.map(l => {
      try { return JSON.parse(l); } catch { return null; }
    }).filter(Boolean);
  } catch {
    return [];
  }
}

function readFullFile(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf-8');
  } catch {
    return '';
  }
}

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 900,
    minHeight: 600,
    titleBarStyle: 'hiddenInset',
    backgroundColor: '#0f0f1a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

// IPC Handlers
ipcMain.handle('get-settings', () => loadSettings());

ipcMain.handle('save-settings', (_, settings) => {
  saveSettings(settings);
  return true;
});

ipcMain.handle('scan-conversations', () => {
  const settings = loadSettings();
  return scanAllConversations(settings);
});

ipcMain.handle('read-conversation', (_, filePath) => {
  return readFullFile(filePath);
});

ipcMain.handle('read-conversation-head', (_, filePath) => {
  return readFileHead(filePath);
});

ipcMain.handle('pick-directory', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory'],
  });
  if (result.canceled) return null;
  return result.filePaths[0];
});

ipcMain.handle('open-in-finder', (_, filePath) => {
  shell.showItemInFolder(filePath);
});

ipcMain.handle('get-file-stats', (_, dirPath) => {
  try {
    const files = fs.readdirSync(dirPath).filter(f => f.endsWith('.jsonl'));
    return { count: files.length, valid: true };
  } catch {
    return { count: 0, valid: false };
  }
});
