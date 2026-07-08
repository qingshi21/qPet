// ===== Electron IPC =====
const { ipcRenderer } = require('electron');

// ===== 从悬浮窗返回时自动显示宠物列表 =====
ipcRenderer.on('auto-show-pet-list', () => {
    const userId = localStorage.getItem('userId');
    if (userId) {
        currentUserId = parseInt(userId);
        showPetList();
    }
});

// ===== 窗口控制 =====
const closeBtn = document.getElementById('closeBtn');
const minimizeBtn = document.getElementById('minimizeBtn');

if (closeBtn) {
    closeBtn.addEventListener('click', () => {
        window.close();
    });
}

if (minimizeBtn) {
    minimizeBtn.addEventListener('click', () => {
        ipcRenderer.send('minimize-launcher');
    });
}

// ===== 消息提示管理（支持错误和成功两种颜色） =====
function showMessage(elementId, message, isSuccess = false) {
    const el = document.getElementById(elementId);
    if (!el) return;

    if (el._timeout) {
        clearTimeout(el._timeout);
        clearTimeout(el._fadeTimeout);
    }

    el.textContent = message;
    el.style.color = isSuccess ? '#27ae60' : '#e74c3c';
    el.classList.remove('fade-out');
    el.style.opacity = '1';

    el._timeout = setTimeout(() => {
        el.classList.add('fade-out');

        el._fadeTimeout = setTimeout(() => {
            el.textContent = '';
            el.classList.remove('fade-out');
            el.style.opacity = '1';
        }, 500);
    }, 10000);
}

function clearError(elementId) {
    const el = document.getElementById(elementId);
    if (!el) return;

    if (el._timeout) {
        clearTimeout(el._timeout);
        clearTimeout(el._fadeTimeout);
    }
    el.textContent = '';
    el.classList.remove('fade-out');
    el.style.opacity = '1';
}

// ===== Tab 切换 =====
const tabs = document.querySelectorAll('.tab');
const loginForm = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');

tabs.forEach(tab => {
    tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');

        if (tab.dataset.tab === 'login') {
            loginForm.style.display = 'flex';
            registerForm.style.display = 'none';
            clearError('regError');
            document.getElementById('loginPassword').disabled = false;
        } else {
            loginForm.style.display = 'none';
            registerForm.style.display = 'flex';
            clearError('loginError');
            document.getElementById('regPassword').disabled = false;
        }
    });
});

// ===== 登录 =====
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;

    if (!username || !password) {
        showMessage('loginError', '请填写完整信息');
        return;
    }

    try {
        const response = await fetch('http://localhost:8080/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        const result = await response.json();

        if (result.code === 200) {
            currentUserId = result.data.userId;
            localStorage.setItem('userId', currentUserId);
            localStorage.setItem('username', result.data.username);

            showMessage('loginError', '✅ 登录成功！', true);
            // 延迟一点切换到宠物列表
            setTimeout(() => {
                showPetList();
            }, 300);
        }
        
    } catch (err) {
        showMessage('loginError', '网络错误，请确认后端服务已启动');
        console.error(err);
    }
});

// ===== 注册 =====
registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('regUsername').value;
    const password = document.getElementById('regPassword').value;

    if (!username || !password) {
        showMessage('regError', '请填写完整信息');
        return;
    }
    if (password.length < 6) {
        showMessage('regError', '密码至少6位');
        return;
    }

    try {
        const response = await fetch('http://localhost:8080/api/auth/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        const result = await response.json();

        if (result.code === 200) {
            document.querySelector('.tab[data-tab="login"]').click();

            const usernameInput = document.getElementById('loginUsername');
            usernameInput.value = username;
            usernameInput.disabled = false;

            const pwdInput = document.getElementById('loginPassword');
            pwdInput.value = '';
            pwdInput.disabled = false;

            document.querySelector('#loginForm button').disabled = false;

            showMessage('loginError', '✅ 注册成功，请登录', true);
        } else {
            showMessage('regError', result.message || '注册失败');
        }
    } catch (err) {
        showMessage('regError', '网络错误，请确认后端服务已启动');
        console.error(err);
    }
});

// ===== 通用请求头 =====
function apiHeaders() {
    return {
        'Content-Type': 'application/json',
        'X-User-Id': String(localStorage.getItem('userId') || '')
    };
}

// ===== 宠物列表相关 =====
const petListContainer = document.getElementById('petListContainer');
const petListEl = document.getElementById('petList');
const createPetBtn = document.getElementById('createPetBtn');
const logoutBtn = document.getElementById('logoutBtn');

let currentUserId = null;
let pets = [];

// 宠物头像 emoji 池
const petEmojis = ['🐱', '🐶', '🐰', '🦊', '🐼', '🐨', '🦄', '🐲', '🐧', '🦋'];
function getPetEmoji(petId) {
    return petEmojis[petId % petEmojis.length];
}

// 获取宠物列表
async function fetchPets() {
    try {
        const response = await fetch('http://localhost:8080/api/pets', {
            headers: apiHeaders()
        });
        const result = await response.json();
        if (result.code === 200) {
            pets = result.data || [];
            renderPetList();
        } else {
            showMessage('loginError', '获取宠物列表失败');
        }
    } catch (err) {
        showMessage('loginError', '网络错误');
        console.error(err);
    }
}

// 渲染宠物列表
function renderPetList() {
    if (!petListEl) return;
    if (pets.length === 0) {
        petListEl.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">✨</div>
                <div>还没有桌宠，点击右上角创建吧</div>
            </div>`;
        return;
    }
    petListEl.innerHTML = pets.map(pet => `
        <div class="pet-card ${pet.isActive ? 'active' : ''}" data-pet-id="${pet.petId}">
            <div class="pet-avatar-icon">${getPetEmoji(pet.petId)}</div>
            <div class="info">
                <span class="name">${pet.name}</span>
                <span class="role">${pet.roleName || '未设定角色'}</span>
            </div>
            <div class="card-actions">
                ${pet.isActive ? '<span class="status">使用中</span>' : ''}
                <button class="btn-delete" data-pet-id="${pet.petId}" title="删除">✕</button>
            </div>
        </div>
    `).join('');

    // 绑定点击事件：选择宠物
    document.querySelectorAll('.pet-card').forEach(card => {
        card.addEventListener('click', (e) => {
            // 点击删除按钮不触发选择
            if (e.target.classList.contains('btn-delete')) return;
            const petId = parseInt(card.dataset.petId);
            selectPet(petId);
        });
    });

    // 绑定删除按钮
    document.querySelectorAll('.btn-delete').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const petId = parseInt(btn.dataset.petId);
            const pet = pets.find(p => p.petId === petId);
            if (pet && confirm(`确定要删除「${pet.name}」吗？`)) {
                deletePet(petId);
            }
        });
    });
}

// 选择宠物（切换激活）
async function selectPet(petId) {
    try {
        const response = await fetch(`http://localhost:8080/api/pets/${petId}/activate`, {
            method: 'PATCH',
            headers: apiHeaders()
        });
        const result = await response.json();
        if (result.code === 200) {
            showMessage('loginError', '✅ 已切换到 ' + result.data.name, true);
            await fetchPets();
            // 通知主进程关闭启动器、打开悬浮窗
            ipcRenderer.send('open-floating');
            ipcRenderer.send('close-launcher');
        } else {
            showMessage('loginError', result.message || '切换失败');
        }
    } catch (err) {
        showMessage('loginError', '网络错误');
        console.error(err);
    }
}

// 删除宠物
async function deletePet(petId) {
    try {
        const response = await fetch(`http://localhost:8080/api/pets/${petId}`, {
            method: 'DELETE',
            headers: apiHeaders()
        });
        const result = await response.json();
        if (result.code === 200) {
            showMessage('loginError', '已删除', true);
            await fetchPets();
        } else {
            showMessage('loginError', result.message || '删除失败');
        }
    } catch (err) {
        showMessage('loginError', '网络错误');
        console.error(err);
    }
}

// 显示宠物列表（隐藏登录/注册表单）
function showPetList() {
    const authPanel = document.querySelector('.auth-panel');
    const petDisplay = document.querySelector('.pet-display');

    if (authPanel) authPanel.style.display = 'none';
    if (petDisplay) petDisplay.style.display = 'none';
    if (petListContainer) {
        petListContainer.style.display = '';
        petListContainer.classList.add('visible');
        fetchPets();
    }
}

// 显示登录/注册表单（隐藏宠物列表）
function showAuthPanel() {
    const authPanel = document.querySelector('.auth-panel');
    const petDisplay = document.querySelector('.pet-display');
    if (authPanel) authPanel.style.display = '';
    if (petDisplay) petDisplay.style.display = '';
    petListContainer.classList.remove('visible');
    petListContainer.style.display = 'none';
}

// ===== 退出登录 =====
if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
        localStorage.clear();
        showAuthPanel();
        showMessage('loginError', '已退出登录');
    });
}

// ===== 创建宠物模态框 =====
const createPetModal = document.getElementById('createPetModal');
const createPetForm = document.getElementById('createPetForm');
const modalCloseBtn = document.getElementById('modalCloseBtn');
const modalCancelBtn = document.getElementById('modalCancelBtn');

function openCreateModal() {
    createPetForm.reset();
    clearError('createPetError');
    createPetModal.style.display = 'flex';
    // 触发 reflow 后再加 show class，让过渡动画生效
    requestAnimationFrame(() => {
        createPetModal.classList.add('show');
    });
}

function closeCreateModal() {
    createPetModal.classList.remove('show');
    setTimeout(() => {
        createPetModal.style.display = 'none';
    }, 250);
}

if (createPetBtn) {
    createPetBtn.addEventListener('click', openCreateModal);
}
if (modalCloseBtn) {
    modalCloseBtn.addEventListener('click', closeCreateModal);
}
if (modalCancelBtn) {
    modalCancelBtn.addEventListener('click', closeCreateModal);
}
// 点击遮罩关闭
if (createPetModal) {
    createPetModal.addEventListener('click', (e) => {
        if (e.target === createPetModal) closeCreateModal();
    });
}

// 提交创建表单
if (createPetForm) {
    createPetForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('petNameInput').value.trim();
        const roleName = document.getElementById('petRoleInput').value.trim();
        const systemPrompt = document.getElementById('petPromptInput').value.trim();

        if (!name) {
            showMessage('createPetError', '请输入宠物名称');
            return;
        }
        if (!systemPrompt) {
            showMessage('createPetError', '请输入性格描述');
            return;
        }

        try {
            const response = await fetch('http://localhost:8080/api/pets', {
                method: 'POST',
                headers: apiHeaders(),
                body: JSON.stringify({ name, roleName, systemPrompt })
            });
            const result = await response.json();
            if (result.code === 200) {
                showMessage('loginError', '✅ 桌宠创建成功！', true);
                closeCreateModal();
                await fetchPets();
            } else {
                showMessage('createPetError', result.message || '创建失败');
            }
        } catch (err) {
            showMessage('createPetError', '网络错误');
            console.error(err);
        }
    });
}
