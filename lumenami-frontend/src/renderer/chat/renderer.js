const { ipcRenderer } = require('electron');

// ===== DOM 元素 =====
const chatArea = document.getElementById('chatArea');
const messageInput = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');
const welcomeMsg = document.getElementById('welcomeMsg');
const petNameEl = document.getElementById('petName');
const petAvatarEl = document.getElementById('petAvatar');
const closeBtn = document.getElementById('closeBtn');
const minimizeBtn = document.getElementById('minimizeBtn');

// 记忆相关 DOM
const memoryBtn = document.getElementById('memoryBtn');
const memorySidebar = document.getElementById('memorySidebar');
const closeMemorySidebar = document.getElementById('closeMemorySidebar');
const memoryList = document.getElementById('memoryList');
const addMemoryBtn = document.getElementById('addMemoryBtn');
const addMemoryModal = document.getElementById('addMemoryModal');
const closeAddMemoryModal = document.getElementById('closeAddMemoryModal');
const cancelAddMemory = document.getElementById('cancelAddMemory');
const confirmAddMemory = document.getElementById('confirmAddMemory');
const editMemoryModal = document.getElementById('editMemoryModal');
const closeEditMemoryModal = document.getElementById('closeEditMemoryModal');
const cancelEditMemory = document.getElementById('cancelEditMemory');
const confirmEditMemory = document.getElementById('confirmEditMemory');
const memoryHistoryModal = document.getElementById('memoryHistoryModal');
const closeHistoryModal = document.getElementById('closeHistoryModal');
const closeHistoryBtn = document.getElementById('closeHistoryBtn');
const historyList = document.getElementById('historyList');

// ===== 状态 =====
let conversationHistory = [];
let currentPet = null;
let isWaitingResponse = false;
let editingMemoryId = null; // 当前正在编辑的记忆ID

// ===== 宠物头像映射 =====
const petEmojis = ['🐱', '🐶', '🐰', '🦊', '🐼', '🐨', '🦄', '🐲', '🐧', '🦋'];
function getPetEmoji(petId) {
    return petEmojis[petId % petEmojis.length];
}

// ===== 窗口控制 =====
if (closeBtn) {
    closeBtn.addEventListener('click', () => {
        window.close();
    });
}

if (minimizeBtn) {
    minimizeBtn.addEventListener('click', () => {
        ipcRenderer.send('minimize-chat');
    });
}

// ===== 接收宠物信息 =====
ipcRenderer.on('set-pet-info', (event, petInfo) => {
    currentPet = petInfo;
    if (petNameEl) {
        petNameEl.textContent = petInfo.name || 'LumenAmi';
    }
    if (petAvatarEl) {
        petAvatarEl.textContent = getPetEmoji(petInfo.petId);
    }
    // 加载历史消息
    loadHistory();
});

// ===== 加载历史消息 =====
async function loadHistory() {
    if (!currentPet || !currentPet.petId) return;
    
    try {
        const userId = localStorage.getItem('userId');
        const response = await fetch(`http://localhost:8080/api/chat/history/${currentPet.petId}`, {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            }
        });
        const result = await response.json();
        
        if (result.code === 200 && result.data && result.data.length > 0) {
            // 隐藏欢迎消息
            if (welcomeMsg) {
                welcomeMsg.style.display = 'none';
            }
            
            // 渲染历史消息
            for (const msg of result.data) {
                const type = msg.role === 'user' ? 'user' : 'ai';
                appendMessage(type, msg.content, false); // false = 不动画
                conversationHistory.push({ role: msg.role, content: msg.content });
            }
        }
    } catch (err) {
        console.error('Failed to load chat history:', err);
    }
}

// ===== 消息输入 =====
messageInput.addEventListener('input', () => {
    // 自动调整高度
    messageInput.style.height = 'auto';
    messageInput.style.height = Math.min(messageInput.scrollHeight, 80) + 'px';
    
    // 更新发送按钮状态
    sendBtn.disabled = !messageInput.value.trim() || isWaitingResponse;
});

// Enter 发送，Shift+Enter 换行
messageInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        if (!sendBtn.disabled) {
            sendMessage();
        }
    }
});

sendBtn.addEventListener('click', () => {
    if (!sendBtn.disabled) {
        sendMessage();
    }
});

// ===== 发送消息 =====
async function sendMessage() {
    const text = messageInput.value.trim();
    if (!text || isWaitingResponse) return;

    // 隐藏欢迎消息
    if (welcomeMsg) {
        welcomeMsg.style.display = 'none';
    }

    // 添加用户消息到界面
    appendMessage('user', text);
    
    // 添加到对话历史
    conversationHistory.push({ role: 'user', content: text });

    // 清空输入框
    messageInput.value = '';
    messageInput.style.height = 'auto';
    sendBtn.disabled = true;
    isWaitingResponse = true;

    // 显示打字指示器
    showTypingIndicator();

    try {
        // 调用后端 API
        const response = await fetch('http://localhost:8080/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': String(localStorage.getItem('userId') || '')
            },
            body: JSON.stringify({
                petId: currentPet?.petId,
                message: text,
                history: conversationHistory.slice(0, -1) // 不包含最后一条（后端会自己构建）
            })
        });

        const result = await response.json();
        
        // 移除打字指示器
        removeTypingIndicator();

        if (result.code === 200) {
            const reply = result.data.reply;
            appendMessage('ai', reply);
            conversationHistory.push({ role: 'assistant', content: reply });
        } else {
            appendMessage('ai', '抱歉，我遇到了一些问题...');
            console.error('Chat error:', result.message);
        }
    } catch (err) {
        removeTypingIndicator();
        appendMessage('ai', '网络错误，请确认后端服务已启动');
        console.error('Chat error:', err);
    }

    isWaitingResponse = false;
    sendBtn.disabled = !messageInput.value.trim();
}

// ===== 消息渲染 =====
function appendMessage(type, text, animate = true) {
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${type}`;
    
    if (!animate) {
        msgDiv.style.animation = 'none';
    }

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    
    if (type === 'ai') {
        avatar.textContent = currentPet ? getPetEmoji(currentPet.petId) : '🐱';
    } else {
        avatar.textContent = '🧑';
    }

    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = text;

    msgDiv.appendChild(avatar);
    msgDiv.appendChild(bubble);
    chatArea.appendChild(msgDiv);

    // 滚动到底部
    scrollToBottom();
}

function showTypingIndicator() {
    const indicator = document.createElement('div');
    indicator.className = 'message ai';
    indicator.id = 'typingIndicator';

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = currentPet ? getPetEmoji(currentPet.petId) : '🐱';

    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.innerHTML = '<div class="typing-indicator"><span></span><span></span><span></span></div>';

    indicator.appendChild(avatar);
    indicator.appendChild(bubble);
    chatArea.appendChild(indicator);

    scrollToBottom();
}

function removeTypingIndicator() {
    const indicator = document.getElementById('typingIndicator');
    if (indicator) {
        indicator.remove();
    }
}

function scrollToBottom() {
    chatArea.scrollTop = chatArea.scrollHeight;
}

// ===== 初始化聚焦 =====
messageInput.focus();

// ===== 记忆管理 =====

// 记忆键名到友好标签的映射
const memoryLabelMap = {
    'user_name': '名字',
    'user_nickname': '昵称',
    'user_age': '年龄',
    'user_gender': '性别',
    'user_occupation': '职业',
    'user_location': '所在地',
    'user_birthday': '生日',
    'user_personality': '性格特点',
    'user_hobby': '兴趣爱好',
    'user_interest': '关注领域',
    'user_pet': '宠物信息',
    'user_family': '家庭成员',
    'current_project': '当前项目',
    'project_tech': '技术栈',
    'project_deadline': '项目截止日期',
    'project_description': '项目描述',
    'work_status': '工作状态',
    'communication_style': '沟通风格',
    'reply_length': '回复长度偏好',
    'topic_preference': '话题偏好',
    'language_preference': '语言偏好',
    'mood': '当前心情',
    'daily_routine': '日常作息',
};

// 类型标签映射
const memoryTypeLabels = {
    'PROFILE': '📋 用户画像',
    'PROJECT': '💼 项目信息',
    'PREFERENCE': '❤️ 偏好设置'
};

// 获取友好标签
function getMemoryLabel(key) {
    return memoryLabelMap[key] || key;
}

// 侧边栏切换
if (memoryBtn) {
    memoryBtn.addEventListener('click', () => {
        memorySidebar.classList.toggle('visible');
        if (memorySidebar.classList.contains('visible')) {
            loadMemories();
        }
    });
}

if (closeMemorySidebar) {
    closeMemorySidebar.addEventListener('click', () => {
        memorySidebar.classList.remove('visible');
    });
}

// 加载记忆列表
async function loadMemories() {
    if (!currentPet || !currentPet.petId) return;
    
    try {
        const userId = localStorage.getItem('userId');
        const response = await fetch(`http://localhost:8080/api/memories/pet/${currentPet.petId}`, {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            }
        });
        const result = await response.json();
        
        if (result.code === 200) {
            renderMemories(result.data || []);
        } else {
            console.error('Failed to load memories:', result.message);
        }
    } catch (err) {
        console.error('Failed to load memories:', err);
        memoryList.innerHTML = '<div class="memory-empty"><div class="memory-empty-icon">⚠️</div><div class="memory-empty-text">加载失败</div></div>';
    }
}

// 渲染记忆列表
function renderMemories(memories) {
    if (!memories || memories.length === 0) {
        memoryList.innerHTML = '<div class="memory-empty"><div class="memory-empty-icon">🧠</div><div class="memory-empty-text">还没有记忆哦<br>和TA多聊聊天吧！</div></div>';
        return;
    }
    
    // 只保留每个key的最新版本
    const latestMap = {};
    for (const mem of memories) {
        const key = mem.memoryKey;
        if (!latestMap[key] || mem.version > latestMap[key].version) {
            latestMap[key] = mem;
        }
    }
    const latestMemories = Object.values(latestMap);
    
    // 按类型分组
    const groups = { PROFILE: [], PROJECT: [], PREFERENCE: [] };
    for (const mem of latestMemories) {
        const type = mem.type || 'PROFILE';
        if (groups[type]) {
            groups[type].push(mem);
        }
    }
    
    let html = '';
    for (const [type, label] of Object.entries(memoryTypeLabels)) {
        const items = groups[type];
        if (!items || items.length === 0) continue;
        
        html += `<div class="memory-group-title">${label}</div>`;
        for (const mem of items) {
            const permanentClass = mem.isPermanent ? ' permanent' : '';
            const typeClass = type.toLowerCase();
            const stars = '★'.repeat(Math.min(mem.importance || 5, 10));
            const permanentBadge = mem.isPermanent ? ' 🔒' : '';
            
            html += `
                <div class="memory-card${permanentClass}" data-id="${mem.id}" data-key="${mem.memoryKey}">
                    <span class="memory-type-badge ${typeClass}">${getMemoryLabel(mem.memoryKey)}${permanentBadge}</span>
                    <div class="memory-value">${escapeHtml(mem.memoryValue)}</div>
                    <div class="memory-meta">
                        <span class="memory-importance" title="重要性 ${mem.importance}/10">${stars}</span>
                        <span>v${mem.version}</span>
                    </div>
                    <div class="memory-actions-card">
                        <button class="btn-memory-action" onclick="editMemory(${mem.id}, '${escapeAttr(mem.memoryKey)}', '${escapeAttr(mem.memoryValue)}', ${mem.importance || 5})">✏️ 编辑</button>
                        <button class="btn-memory-action" onclick="viewHistory('${escapeAttr(mem.memoryKey)}')">📜 历史</button>
                        <button class="btn-memory-action delete" onclick="deleteMemory(${mem.id}, '${escapeAttr(getMemoryLabel(mem.memoryKey))}')">🗑️ 删除</button>
                    </div>
                </div>
            `;
        }
    }
    
    memoryList.innerHTML = html;
}

// HTML 转义
function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function escapeAttr(text) {
    if (!text) return '';
    return text.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, '\\n');
}

// 添加记忆模态框
if (addMemoryBtn) {
    addMemoryBtn.addEventListener('click', () => {
        addMemoryModal.classList.add('visible');
    });
}

if (closeAddMemoryModal) {
    closeAddMemoryModal.addEventListener('click', () => {
        addMemoryModal.classList.remove('visible');
    });
}

if (cancelAddMemory) {
    cancelAddMemory.addEventListener('click', () => {
        addMemoryModal.classList.remove('visible');
    });
}

if (confirmAddMemory) {
    confirmAddMemory.addEventListener('click', async () => {
        const type = document.getElementById('memoryType').value;
        const key = document.getElementById('memoryKey').value.trim();
        const value = document.getElementById('memoryValue').value.trim();
        const importance = parseInt(document.getElementById('memoryImportance').value) || 5;
        
        if (!key || !value) {
            alert('请填写记忆键名和值');
            return;
        }
        
        try {
            const userId = localStorage.getItem('userId');
            const response = await fetch('http://localhost:8080/api/memories', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Id': userId
                },
                body: JSON.stringify({
                    petId: currentPet.petId,
                    key: key,
                    value: value,
                    type: type,
                    importance: importance
                })
            });
            const result = await response.json();
            
            if (result.code === 200) {
                addMemoryModal.classList.remove('visible');
                // 清空表单
                document.getElementById('memoryKey').value = '';
                document.getElementById('memoryValue').value = '';
                document.getElementById('memoryImportance').value = '5';
                // 刷新列表
                loadMemories();
            } else {
                alert('添加失败：' + result.message);
            }
        } catch (err) {
            alert('网络错误，请重试');
        }
    });
}

// 编辑记忆
window.editMemory = async function(id, key, value, importance) {
    editingMemoryId = id;
    document.getElementById('editMemoryLabel').value = getMemoryLabel(key);
    document.getElementById('editMemoryValue').value = value;
    document.getElementById('editMemoryImportance').value = importance;
    editMemoryModal.classList.add('visible');
};

if (closeEditMemoryModal) {
    closeEditMemoryModal.addEventListener('click', () => {
        editMemoryModal.classList.remove('visible');
        editingMemoryId = null;
    });
}

if (cancelEditMemory) {
    cancelEditMemory.addEventListener('click', () => {
        editMemoryModal.classList.remove('visible');
        editingMemoryId = null;
    });
}

if (confirmEditMemory) {
    confirmEditMemory.addEventListener('click', async () => {
        if (!editingMemoryId) return;
        
        const value = document.getElementById('editMemoryValue').value.trim();
        const importance = parseInt(document.getElementById('editMemoryImportance').value) || 5;
        
        if (!value) {
            alert('记忆值不能为空');
            return;
        }
        
        try {
            const userId = localStorage.getItem('userId');
            const response = await fetch(`http://localhost:8080/api/memories/${editingMemoryId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Id': userId
                },
                body: JSON.stringify({ value, importance })
            });
            const result = await response.json();
            
            if (result.code === 200) {
                editMemoryModal.classList.remove('visible');
                editingMemoryId = null;
                loadMemories();
            } else {
                alert('修改失败：' + result.message);
            }
        } catch (err) {
            alert('网络错误，请重试');
        }
    });
}

// 删除记忆
window.deleteMemory = async function(id, label) {
    if (!confirm(`确定要删除「${label}」这条记忆吗？`)) return;
    
    try {
        const userId = localStorage.getItem('userId');
        const response = await fetch(`http://localhost:8080/api/memories/${id}`, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            }
        });
        const result = await response.json();
        
        if (result.code === 200) {
            loadMemories();
        } else {
            alert('删除失败：' + result.message);
        }
    } catch (err) {
        alert('网络错误，请重试');
    }
};

// 查看记忆历史
window.viewHistory = async function(key) {
    if (!currentPet || !currentPet.petId) return;
    
    try {
        const userId = localStorage.getItem('userId');
        const response = await fetch(`http://localhost:8080/api/memories/pet/${currentPet.petId}/history/${key}`, {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId
            }
        });
        const result = await response.json();
        
        if (result.code === 200) {
            renderHistory(result.data || [], key);
            memoryHistoryModal.classList.add('visible');
        } else {
            alert('加载历史失败');
        }
    } catch (err) {
        alert('网络错误，请重试');
    }
};

function renderHistory(memories, key) {
    if (!memories || memories.length === 0) {
        historyList.innerHTML = '<div class="memory-empty"><div class="memory-empty-text">暂无历史记录</div></div>';
        return;
    }
    
    let html = '';
    memories.forEach((mem, index) => {
        const isCurrent = index === 0 ? ' current' : '';
        const versionLabel = index === 0 ? '当前版本' : `v${mem.version}`;
        const time = mem.createdAt ? new Date(mem.createdAt).toLocaleString('zh-CN') : '';
        
        html += `
            <div class="history-item${isCurrent}">
                <div class="history-version">${versionLabel}</div>
                <div class="history-value">${escapeHtml(mem.memoryValue)}</div>
                <div class="history-time">${time}</div>
                ${mem.previousValue ? `<div class="history-time">旧值：${escapeHtml(mem.previousValue)}</div>` : ''}
            </div>
        `;
    });
    
    historyList.innerHTML = html;
}

if (closeHistoryModal) {
    closeHistoryModal.addEventListener('click', () => {
        memoryHistoryModal.classList.remove('visible');
    });
}

if (closeHistoryBtn) {
    closeHistoryBtn.addEventListener('click', () => {
        memoryHistoryModal.classList.remove('visible');
    });
}
