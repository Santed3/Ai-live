const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  chat: (text, voice) => ipcRenderer.invoke('chat', { text, voice }),
  chips: () => ipcRenderer.invoke('chips'),
  reply: () => ipcRenderer.invoke('reply'),
  transform: (instruction, text) => ipcRenderer.invoke('transform', { instruction, text }),
  insert: (text, send) => ipcRenderer.invoke('insert', { text, send }),
  copy: (text) => ipcRenderer.invoke('copy', text),
  newChat: () => ipcRenderer.invoke('new-chat'),
  hide: () => ipcRenderer.invoke('set-mode', 'hide'),
  quit: () => ipcRenderer.invoke('quit'),
  stt: (buf) => ipcRenderer.invoke('stt', buf),
  getSettings: () => ipcRenderer.invoke('settings-get'),
  setSettings: (s) => ipcRenderer.invoke('settings-set', s),
  confirmReply: (id, ok) => ipcRenderer.send('confirm-reply', { id, ok }),
  onStatus: (cb) => ipcRenderer.on('status', (_e, t) => cb(t)),
  onConfirm: (cb) => ipcRenderer.on('confirm-request', (_e, m) => cb(m)),
  onShown: (cb) => ipcRenderer.on('panel-shown', () => cb()),
  // dot
  dotClick: () => ipcRenderer.send('dot-click'),
  dotDouble: () => ipcRenderer.send('dot-dbl'),
  dotPanel: () => ipcRenderer.send('dot-panel'),
  dotMove: (x, y) => ipcRenderer.send('dot-move', { x, y }),
  dotSave: () => ipcRenderer.send('dot-save'),
  // answer bubble
  onAnswer: (cb) => ipcRenderer.on('answer-text', (_e, t) => cb(t)),
  answerClick: () => ipcRenderer.send('answer-click'),
});
