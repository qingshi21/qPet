const { app, BrowserWindow, screen } = require('electron');
const path = require('path');

// ========== 启动器窗口 ==========
let launcherWindow = null;

function createLauncher(autoShowPetList = false) {
    if (launcherWindow) {
        launcherWindow.show();
        if (autoShowPetList) {
            launcherWindow.webContents.send('auto-show-pet-list');
        }
        return launcherWindow;
    }

    launcherWindow = new BrowserWindow({
        width: 480,
        height: 680,
        frame: false,
        transparent: true,
        resizable: false,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        }
    });
    launcherWindow.loadFile('./src/renderer/launcher/index.html');

    // 如果是从悬浮窗返回，自动跳到宠物列表
    if (autoShowPetList) {
        launcherWindow.webContents.on('did-finish-load', () => {
            launcherWindow.webContents.send('auto-show-pet-list');
        });
    }

    launcherWindow.on('closed', () => {
        launcherWindow = null;
    });

    return launcherWindow;
}

// ========== 悬浮窗 ==========
let floatingWindow = null;

function createFloating() {
    if (floatingWindow) {
        floatingWindow.show();
        return floatingWindow;
    }

    floatingWindow = new BrowserWindow({
        width: 400,
        height: 400,
        frame: false,
        transparent: true,
        alwaysOnTop: true,
        resizable: true,   // 必须 true，否则 setBounds 在某些 Electron 版本下不生效
        skipTaskbar: true,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        }
    });

    floatingWindow.loadFile('./src/renderer/floating/index.html');
    
    floatingWindow.on('closed', () => {
        floatingWindow = null;
        stopMouseTracking();  // 关闭悬浮窗时停止追踪
    });

    return floatingWindow;
}

// ========== 聊天窗口 ==========
let chatWindow = null;

function createChatWindow(petInfo) {
    if (chatWindow && !chatWindow.isDestroyed()) {
        // 复用已有窗口，只发送新宠物信息（不销毁重建，避免 WebSocket 断连）
        chatWindow.show();
        chatWindow.focus();
        chatWindow.webContents.send('set-pet-info', petInfo);
        return chatWindow;
    }

    chatWindow = new BrowserWindow({
        width: 450,
        height: 600,
        minWidth: 450,
        minHeight: 500,
        frame: false,
        transparent: true,
        resizable: true,
        minimizable: true,
        skipTaskbar: false,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false
        }
    });

    chatWindow.loadFile('./src/renderer/chat/index.html');

    // 传递宠物信息到聊天窗口
    chatWindow.webContents.on('did-finish-load', () => {
        if (petInfo) {
            chatWindow.webContents.send('set-pet-info', petInfo);
        }
    });

    chatWindow.on('closed', () => {
        chatWindow = null;
    });

    return chatWindow;
}

// ========== 暴露方法给渲染进程 ==========
const { ipcMain } = require('electron');

ipcMain.on('close-launcher', () => {
    if (launcherWindow && !launcherWindow.isDestroyed()) {
        launcherWindow.close();
        launcherWindow = null;
    }
});

ipcMain.on('minimize-launcher', () => {
    if (launcherWindow) {
        launcherWindow.minimize();
    }
});

ipcMain.on('open-floating', () => {
    createFloating();
    startMouseTracking();  // 开启全局鼠标追踪
});

ipcMain.on('open-launcher', () => {
    // 关闭悬浮窗，重新打开启动器并自动显示宠物列表
    if (floatingWindow && !floatingWindow.isDestroyed()) {
        floatingWindow.close();
        floatingWindow = null;
    }
    // 同时关闭聊天窗口，防止旧 WebSocket 连接残留
    if (chatWindow && !chatWindow.isDestroyed()) {
        chatWindow.close();
        chatWindow = null;
    }
    createLauncher(true);
});

ipcMain.on('open-chat', (event, petInfo) => {
    createChatWindow(petInfo);
});

ipcMain.on('minimize-chat', () => {
    if (chatWindow) {
        chatWindow.minimize();
    }
});

// ========== 口型同步：聊天窗口 → 悬浮窗 ==========
ipcMain.on('lip-sync-start', (event, { duration }) => {
    if (floatingWindow && !floatingWindow.isDestroyed()) {
        floatingWindow.webContents.send('lip-sync-start', { duration });
    }
});

ipcMain.on('lip-sync-stop', () => {
    if (floatingWindow && !floatingWindow.isDestroyed()) {
        floatingWindow.webContents.send('lip-sync-stop');
    }
});

// ========== 全局鼠标位置追踪（悬浮窗窗口外跟随） ==========
let mouseTrackInterval = null;

function startMouseTracking() {
    if (mouseTrackInterval) return;
    // 每 50ms 轮询一次全局鼠标位置（约 20fps，足够头部跟随）
    mouseTrackInterval = setInterval(() => {
        if (!floatingWindow || floatingWindow.isDestroyed()) return;
        const { x, y } = screen.getCursorScreenPoint();
        const bounds = floatingWindow.getBounds();
        // 转换为悬浮窗本地坐标（可以是负数或超出窗口范围）
        const localX = x - bounds.x;
        const localY = y - bounds.y;
        floatingWindow.webContents.send('global-mouse-move', { localX, localY, winW: bounds.width, winH: bounds.height });
    }, 50);
}

function stopMouseTracking() {
    if (mouseTrackInterval) {
        clearInterval(mouseTrackInterval);
        mouseTrackInterval = null;
    }
}

// ========== 悬浮窗动态调整大小（模型缩放驱动） ==========
ipcMain.on('resize-floating', (event, { width, height }) => {
    if (!floatingWindow || floatingWindow.isDestroyed()) return;
    const w = Math.max(100, Math.round(width));
    const h = Math.max(100, Math.round(height));
    // 先 setSize，再 setPosition 保持居中
    floatingWindow.setSize(w, h);
    const bounds = floatingWindow.getBounds();
    const newX = Math.max(0, Math.round(bounds.x - (w - bounds.width) / 2));
    const newY = Math.max(0, Math.round(bounds.y - (h - bounds.height) / 2));
    floatingWindow.setPosition(newX, newY);
});

// ========== 应用生命周期 ==========
app.whenReady().then(() => {
    createLauncher();
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});