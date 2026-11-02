const { app, BrowserWindow, shell, Menu } = require('electron');
const path = require('path');
app.setName('Anime Draft Duel');
function createWindow () {
  const win = new BrowserWindow({
    width: 1280, height: 820, minWidth: 420, minHeight: 600,
    title: 'Anime Draft Duel', backgroundColor: '#0d0a14', autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  Menu.setApplicationMenu(null);
  // les liens (recherche d'images) s'ouvrent dans le navigateur de l'utilisateur
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file:')) { e.preventDefault(); if (/^https?:/.test(url)) shell.openExternal(url); } });
  win.loadFile(path.join(__dirname, 'index.html'));
}
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
