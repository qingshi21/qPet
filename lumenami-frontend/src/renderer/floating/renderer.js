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

// ===== 交互状态 =====
let isMouseOnBody = false;        // 鼠标是否在模型身体区域
let idleTimer = null;             // 待机动作定时器

const IDLE_INTERVAL_MIN = 8000;   // 待机动作最小间隔(ms)
const IDLE_INTERVAL_MAX = 15000;  // 待机动作最大间隔(ms)

// ===== 鼠标跟随参数 =====
const FOLLOW_AMPLIFY = 1.8;       // 跟随幅度放大系数（>1 让转头更明显）
const FOLLOW_SMOOTH = 0.15;       // 平滑插值系数（0~1，越大跟随越快）
let targetFocusX = 0;             // 目标焦点 X（模型本地坐标）
let targetFocusY = 0;             // 目标焦点 Y
let currentFocusX = 0;            // 当前插值中的焦点 X
let currentFocusY = 0;            // 当前插值中的焦点 Y
let isMouseInsideWindow = false;  // 鼠标是否在窗口内

// ===== 口型同步参数 =====
let lipSyncActive = false;        // 是否正在播放口型动画
let lipSyncTimer = null;          // 口型持续时间定时器
let lipSyncStartTime = 0;        // 口型动画开始时间

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

        // ===== 初始化交互系统 =====
        setupInteraction(canvas);

        // ===== 初始化口型同步 =====
        setupLipSync();

        console.log('Live2D model loaded successfully');
        console.log('Available motion groups:', Object.keys(live2dModel.internalModel.settings.motions || {}));
    } catch (err) {
        console.error('Live2D 模型加载失败:', err);
    }
}

// ===== 交互系统：鼠标跟随 + 戳戳互动 + 待机循环 =====
function setupInteraction(canvas) {
    const canvasW = canvas.offsetWidth;
    const canvasH = canvas.offsetHeight;
    const centerX = canvasW / 2;
    const centerY = canvasH / 2;

    // ----- 1. 窗口内鼠标跟随（DOM 事件） -----
    document.addEventListener('mousemove', (e) => {
        if (!live2dModel) return;
        isMouseInsideWindow = true;

        const rect = canvas.getBoundingClientRect();
        const mouseX = e.clientX - rect.left;
        const mouseY = e.clientY - rect.top;

        // 计算相对中心的偏移，乘以放大系数
        updateFollowTarget(mouseX, mouseY, centerX, centerY);

        // 实时检测鼠标是否在身体区域
        const hitAreas = live2dModel.hitTest(mouseX, mouseY);
        isMouseOnBody = hitAreas.includes('Body');
        canvas.style.cursor = isMouseOnBody ? 'pointer' : 'default';
    });

    // 鼠标离开窗口时不立即重置，交给全局追踪接管
    document.addEventListener('mouseleave', () => {
        isMouseInsideWindow = false;
        isMouseOnBody = false;
        canvas.style.cursor = 'default';
    });

    // ----- 2. 全局鼠标追踪（IPC 来自主进程，窗口外也能跟随） -----
    ipcRenderer.on('global-mouse-move', (event, { localX, localY, winW, winH }) => {
        if (!live2dModel || isMouseInsideWindow) return; // 窗口内用 DOM 事件更精确

        // localX/localY 是鼠标相对于悬浮窗的坐标，可以超出 [0, winW] 范围
        // 映射到 canvas 坐标系（canvas 大致在窗口中心，带 MODEL_PADDING 边距）
        const canvasRect = canvas.getBoundingClientRect();
        const mappedX = localX - canvasRect.left;
        const mappedY = localY - canvasRect.top;

        updateFollowTarget(mappedX, mappedY, centerX, centerY);
    });

    // ----- 3. 戳戳互动：点击触发不同动作 -----
    canvas.addEventListener('click', (e) => {
        if (!live2dModel) return;

        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        // 命中检测
        const hitAreas = live2dModel.hitTest(x, y);
        console.log('[click] x:', x.toFixed(0), 'y:', y.toFixed(0), 'hits:', hitAreas);

        if (hitAreas.includes('Body')) {
            // 判断点击位置在模型上半部分（头部）还是下半部分（身体）
            const modelTop = live2dModel.y - live2dModel.height / 2;
            const headThreshold = modelTop + live2dModel.height * 0.4;
            console.log('[click] modelTop:', modelTop.toFixed(0), 'threshold:', headThreshold.toFixed(0), 'isHead:', y < headThreshold);

            if (y < headThreshold) {
                // 戳到头 → Happy
                console.log('[click] → Happy!');
                playMotion('Happy', 0, 'FORCE');
            } else {
                // 戳到身体 → Tap@Body
                console.log('[click] → Tap@Body');
                playMotion('Tap@Body', 0, 'FORCE');
            }
        } else if (hitAreas.length > 0) {
            // 戳到其他命中区域 → Tap
            console.log('[click] → Tap (other hit area)');
            playMotion('Tap', undefined, 'FORCE');
        } else {
            console.log('[click] → miss (no hit area)');
        }
    });

    // ----- 4. 待机动作自动循环 -----
    scheduleIdleMotion();

    // ----- 5. 启动跟随插值动画循环 -----
    // 初始化焦点到中心，避免加载时宠物看左上角
    targetFocusX = centerX;
    targetFocusY = centerY;
    currentFocusX = centerX;
    currentFocusY = centerY;
    startFollowLoop();
}

// 计算跟随目标（带放大系数）
function updateFollowTarget(mouseX, mouseY, centerX, centerY) {
    // 鼠标相对中心的偏移 × 放大系数
    const offsetX = (mouseX - centerX) * FOLLOW_AMPLIFY;
    const offsetY = (mouseY - centerY) * FOLLOW_AMPLIFY;
    targetFocusX = centerX + offsetX;
    targetFocusY = centerY + offsetY;
}

// 平滑插值循环：每帧将当前焦点向目标插值，然后调用 focus()
function startFollowLoop() {
    function tick() {
        if (live2dModel) {
            // 平滑插值
            currentFocusX += (targetFocusX - currentFocusX) * FOLLOW_SMOOTH;
            currentFocusY += (targetFocusY - currentFocusY) * FOLLOW_SMOOTH;
            live2dModel.focus(currentFocusX, currentFocusY);

            // 口型动画更新（在 focus 之后，确保嘴部参数被覆盖）
            updateLipSync();
        }
        requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
}

// ===== 口型同步系统：接收聊天窗口指令，驱动 ParamMouthOpenY =====
function setupLipSync() {
    // 监听主进程转发的口型开始指令
    ipcRenderer.on('lip-sync-start', (event, { duration }) => {
        startLipSync(duration);
    });

    // 监听口型停止指令
    ipcRenderer.on('lip-sync-stop', () => {
        stopLipSync();
    });
}

function startLipSync(durationMs) {
    // 清除上一次的定时器
    if (lipSyncTimer) {
        clearTimeout(lipSyncTimer);
    }

    lipSyncActive = true;
    lipSyncStartTime = performance.now();

    // 按文本长度设定持续时间，到期自动停止
    lipSyncTimer = setTimeout(() => {
        stopLipSync();
    }, durationMs);
}

function stopLipSync() {
    lipSyncActive = false;
    if (lipSyncTimer) {
        clearTimeout(lipSyncTimer);
        lipSyncTimer = null;
    }
    // 重置嘴巴参数
    if (live2dModel) {
        try {
            live2dModel.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', 0);
        } catch (e) {
            // 参数不存在时忽略
        }
    }
}

// 每帧更新口型动画（在 followLoop 的 tick 中调用）
function updateLipSync() {
    if (!lipSyncActive || !live2dModel) return;

    try {
        const elapsed = performance.now() - lipSyncStartTime;
        // 正弦波驱动嘴部张开，频率 8Hz，幅度 0.6
        // 加 abs 让嘴巴只张不闭（正弦波负值时归零）
        const raw = Math.sin(elapsed * 0.008 * Math.PI * 2);
        const value = Math.abs(raw) * 0.6;
        live2dModel.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', value);
    } catch (e) {
        // 参数不存在或模型未就绪时忽略
    }
}

// 播放动作，所有调用均使用 FORCE 优先级，可随时打断当前动画
function playMotion(group, index, priority) {
    if (!live2dModel) return Promise.resolve(false);

    clearIdleTimer();

    const priorityMap = { 'NONE': 0, 'IDLE': 1, 'NORMAL': 2, 'FORCE': 3 };
    const p = priorityMap[priority] ?? 3; // 默认 FORCE，确保能打断

    return live2dModel.motion(group, index, p).then((success) => {
        scheduleIdleMotion();
        return success;
    }).catch(() => {
        scheduleIdleMotion();
    });
}

// 调度下一次idle动作
function scheduleIdleMotion() {
    clearIdleTimer();
    const delay = IDLE_INTERVAL_MIN + Math.random() * (IDLE_INTERVAL_MAX - IDLE_INTERVAL_MIN);
    idleTimer = setTimeout(() => {
        if (live2dModel) {
            // 随机播放 Idle 组中的动作（m01/m02/m05）
            playMotion('Idle', undefined, 'IDLE');
        }
        // 无论是否播放成功，继续调度下一次
        scheduleIdleMotion();
    }, delay);
}

function clearIdleTimer() {
    if (idleTimer) {
        clearTimeout(idleTimer);
        idleTimer = null;
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