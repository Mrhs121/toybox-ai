const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  getSettings: () => ipcRenderer.invoke('get-settings'),
  saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),
  scanConversations: () => ipcRenderer.invoke('scan-conversations'),
  readConversation: (filePath) => ipcRenderer.invoke('read-conversation', filePath),
  readConversationHead: (filePath) => ipcRenderer.invoke('read-conversation-head', filePath),
  pickDirectory: () => ipcRenderer.invoke('pick-directory'),
  openInFinder: (filePath) => ipcRenderer.invoke('open-in-finder', filePath),
  getFileStats: (dirPath) => ipcRenderer.invoke('get-file-stats', dirPath),
});
