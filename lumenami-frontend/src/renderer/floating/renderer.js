const { ipcRenderer } = require('electron');
const { Application, Ticker } = require('pixi.js');
// 使用 cubism4 模块，支持 .model3.json 格式
const { Live2DModel } = require('pixi-live2d-display/cubism4');

// ===== DOM 元素 =====
const closeBtn = document.getElementById('closeBtn');
const contextMenu = document.getElementById('contextMenu');
const menuBack = document.getElementById('menuBack');
const menuChat = document.getElementById('menuChat');
const menuQuit = document.getElementById('menuQuit');

// 当前激活的宠物信息
let currentPet = null;
let live2dModel = null;
let app = null;

// ===== 缩放控制 =====
let scaleFactor = 1.0;       // 用户缩放倍率（滚轮控制）
const SCALE_MIN = 0.3;
const SCALE_MAX = 3.0;
const SCALE_STEP = 0.05;     // 每次滚轮步进（改小更丝滑）
const MODEL_PADDING = 20;    // 模型与窗口边缘间距(px)
let baseScale = 1;           // 模型加载时的基础缩放
let modelOriginalWidth = 0;  // 模型原始宽度（未缩放）
let modelOriginalHeight = 0; // 模型原始高度（未缩放）

// ===== 防抖/节流控制 =====
let resizeDebounceTimer = null;  // 窗口resize防抖定时器
const RESIZE_DEBOUNCE_DELAY = 25; // 延迟ms，连续滚轮时只执行最后一次

// ===== 获取当前激活的宠物信息 =====
async function fetchActivePet() {
    try {
        const userId = localStorage.getItem('userId');
        if (!userId) return;
        
        const response = await fetch('http://localhost:8080/api/pets', {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + (localStorage.getItem('token') || '')
            }
        });
        const result = await response.json();
        if (result.code === 200 && result.data) {
            currentPet = result.data.find(p => p.isActive) || null;
        }
    } catch (err) {
        console.error('Failed to fetch active pet:', err);
    }
}

// ===== 关闭按钮 =====
if (closeBtn) {
    closeBtn.addEventListener('click', () => {
        window.close();
    });
}

// ===== 右键菜单 =====
function showMenu(x, y) {
    contextMenu.style.left = x + 'px';
    contextMenu.style.top = y + 'px';
    contextMenu.classList.add('show');
}

function hideMenu() {
    contextMenu.classList.remove('show');
}

document.body.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    let x = e.clientX;
    let y = e.clientY;
    if (x + 150 > window.innerWidth) x = window.innerWidth - 155;
    if (y + 120 > window.innerHeight) y = window.innerHeight - 125;
    showMenu(x, y);
});

document.addEventListener('click', (e) => {
    if (!contextMenu.contains(e.target)) {
        hideMenu();
    }
});

// ===== 菜单项事件 =====
if (menuBack) {
    menuBack.addEventListener('click', () => {
        hideMenu();
        ipcRenderer.send('open-launcher');
    });
}

if (menuChat) {
    menuChat.addEventListener('click', () => {
        hideMenu();
        ipcRenderer.send('open-chat', currentPet);
    });
}

if (menuQuit) {
    menuQuit.addEventListener('click', () => {
        hideMenu();
        window.close();
    });
}

// ===== Live2D 初始化 =====
async function initLive2D() {
    try {
        // 获取 canvas 元素
        const canvas = document.getElementById('live2d-canvas');
        if (!canvas) {
            console.error('live2d-canvas not found');
            return;
        }

        // 创建 PixiJS 应用
        const dpr = window.devicePixelRatio || 1;
        app = new Application({
            view: canvas,
            transparent: true,
            width: window.innerWidth,
            height: window.innerHeight,
            backgroundAlpha: 0,
            resolution: dpr,      // 匹配屏幕物理像素比，保证清晰
            autoStart: false,
            eventMode: 'none',
        });

        // 注册 Ticker 给 Live2DModel
        Live2DModel.registerTicker(Ticker);

        // 窗口大小变化时同步渲染器（仅更新渲染尺寸，不重新定位模型）
        window.addEventListener('resize', () => {
            if (app && app.renderer) {
                app.renderer.resize(window.innerWidth, window.innerHeight);
                // 同步 canvas CSS 显示尺寸
                canvas.style.width = window.innerWidth + 'px';
                canvas.style.height = window.innerHeight + 'px';
            }
        });

        // 加载 Live2D 模型
        const modelPath = './live2d/hiyori_pro/hiyori_pro_t11.model3.json';
        live2dModel = await Live2DModel.from(modelPath, {
            autoInteract: false,  // 禁用自动交互，避免 PixiJS v7 API 不兼容问题
        });

        // 调整模型位置和大小（居中显示）
        live2dModel.anchor.set(0.5, 0.5);

        // 保存模型原始尺寸（缩放前的 width/height）
        modelOriginalWidth = live2dModel.width;
        modelOriginalHeight = live2dModel.height;

        // 计算基础缩放：让模型适配当前窗口
        baseScale = Math.min(
            (window.innerWidth * 0.8) / modelOriginalWidth,
            (window.innerHeight * 0.8) / modelOriginalHeight
        );
        live2dModel.scale.set(baseScale * scaleFactor);
        app.stage.addChild(live2dModel);

        // 启动渲染循环
        app.start();

        // 模型加载后，立即让窗口匹配模型实际渲染大小
        updateWindowSize();

        // ===== 滚轮缩放：改模型 scale → 算像素尺寸 → 窗口跟着变（防抖） =====
        document.addEventListener('wheel', (e) => {
            e.preventDefault();
            const delta = e.deltaY > 0 ? -SCALE_STEP : SCALE_STEP;
            const newScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, scaleFactor + delta));
            if (newScale !== scaleFactor) {
                scaleFactor = newScale;
                // 立即更新模型scale（视觉反馈快）
                live2dModel.scale.set(baseScale * scaleFactor);
                // 窗口resize防抖执行
                updateWindowSize();
            }
        }, { passive: false });

        // 强制设置 canvas 光标样式（PixiJS 可能会覆盖）
        canvas.style.cursor = 'pointer';

        // 自动更新物理和表情
        live2dModel.autoUpdate = true;

        console.log('Live2D model loaded successfully');
    } catch (err) {
        console.error('Live2D 模型加载失败:', err);
    }
}

// ===== 窗口尺寸同步：模型实际渲染大小 → 窗口大小（带防抖） =====
function updateWindowSize() {
    if (!live2dModel || !app) return;

    const canvas = document.getElementById('live2d-canvas');

    // 用原始尺寸 × 当前总缩放，得到实际渲染像素（逻辑像素）
    const totalScale = baseScale * scaleFactor;
    const renderedW = Math.ceil(modelOriginalWidth * totalScale);
    const renderedH = Math.ceil(modelOriginalHeight * totalScale);
    const newWidth = renderedW + MODEL_PADDING * 2;
    const newHeight = renderedH + MODEL_PADDING * 2;

    // 立即更新 PixiJS 渲染器和 canvas CSS（模型视觉立即变化，不卡）
    app.renderer.resize(newWidth, newHeight);
    canvas.style.width = newWidth + 'px';
    canvas.style.height = newHeight + 'px';
    live2dModel.position.set(newWidth / 2, newHeight / 2);

    console.log(`[scale] original: ${modelOriginalWidth}x${modelOriginalHeight}, scale: ${totalScale.toFixed(2)}, rendered: ${renderedW}x${renderedH}`);

    // 窗口resize防抖：连续滚轮时只执行最后一次，避免频繁调用Electron API导致卡顿
    if (resizeDebounceTimer) {
        clearTimeout(resizeDebounceTimer);
    }
    resizeDebounceTimer = setTimeout(() => {
        console.log(`[window resize] ${newWidth}x${newHeight}`);
        ipcRenderer.send('resize-floating', { width: newWidth, height: newHeight });
        resizeDebounceTimer = null;
    }, RESIZE_DEBOUNCE_DELAY);
}

// ===== 启动 =====
fetchActivePet();
initLive2D();