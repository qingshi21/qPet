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

// ===== 状态 =====
let conversationHistory = [];
let currentPet = null;
let isWaitingResponse = false;

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
