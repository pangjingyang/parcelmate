/* ParcelMate 前端 —— 纯原生 JS，没有任何构建步骤，改完刷新就生效。 */

const $ = (sel) => document.querySelector(sel);

const state = {
  me: null,          // /api/me 的返回
  tab: 'active',     // 当前 tab
};

/* ---------------- 通用工具 ---------------- */

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  let data = null;
  try { data = await res.json(); } catch (e) { /* 可能是空响应 */ }
  if (!res.ok) {
    throw new Error((data && data.error) || `请求失败（${res.status}）`);
  }
  return data;
}

let toastTimer = null;
function toast(message) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 2600);
}

function colorOf(name) {
  let h = 0;
  for (const ch of name) h = (h * 31 + ch.codePointAt(0)) % 360;
  return `hsl(${h} 52% 46%)`;
}

/* ---------------- 启动 ---------------- */

async function boot() {
  try {
    state.me = await api('/api/me');
  } catch (e) {
    toast(e.message);
    return;
  }
  if (state.me.joined) {
    showApp();
    await loadList();
    refreshBell();
  } else {
    $('#welcome').classList.remove('hidden');
  }
}

/* ---------------- 欢迎页：建房 / 加入 ---------------- */

$('#createForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = $('#roomName').value.trim();
  if (!name) return;
  try {
    const room = await api('/api/rooms', {
      method: 'POST',
      body: JSON.stringify({ name }),
    });
    $('#joinCode').value = room.code;
    $('#joinNickname').focus();
    toast(`宿舍已建好，房间码 ${room.code}，填个昵称加入吧`);
  } catch (err) {
    toast(err.message);
  }
});

$('#joinForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const code = $('#joinCode').value.trim().toUpperCase();
  const nickname = $('#joinNickname').value.trim();
  if (!code || !nickname) return;
  try {
    state.me = await api('/api/rooms/join', {
      method: 'POST',
      body: JSON.stringify({ code, nickname }),
    });
    $('#welcome').classList.add('hidden');
    showApp();
    await loadList();
  } catch (err) {
    toast(err.message);
  }
});

/* ---------------- 主界面 ---------------- */

function showApp() {
  const { room, nickname, members } = state.me;
  $('#app').classList.remove('hidden');
  $('#roomNameLabel').textContent = room.name;
  $('#roomCode').textContent = room.code;
  $('#meName').textContent = nickname;

  const box = $('#memberAvatars');
  box.innerHTML = '';
  const shown = members.slice(0, 4);
  shown.forEach((m) => {
    const el = document.createElement('div');
    el.className = 'avatar' + (m.online ? ' online' : '');
    el.style.background = colorOf(m.nickname);
    el.textContent = m.nickname.slice(0, 1);
    el.title = `${m.nickname} · ${m.online ? '在线' : m.lastActiveText + ' 活跃'}`;
    box.appendChild(el);
  });
  if (members.length > shown.length) {
    const more = document.createElement('div');
    more.className = 'avatar more';
    more.textContent = '+' + (members.length - shown.length);
    more.title = members.slice(shown.length).map((m) => m.nickname).join('、');
    box.appendChild(more);
  }
}

document.querySelectorAll('.tab').forEach((btn) => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    state.tab = btn.dataset.tab;
    loadList();
  });
});

async function loadList() {
  let path = '/api/parcels';
  if (state.tab === 'mine') path = '/api/parcels/mine?asTaker=false';
  if (state.tab === 'taken') path = '/api/parcels/mine?asTaker=true';
  if (state.tab === 'history') path = '/api/parcels/history';

  const list = $('#list');
  try {
    const parcels = await api(path);
    list.innerHTML = '';
    $('#empty').classList.toggle('hidden', parcels.length > 0);
    parcels.forEach((p) => list.appendChild(renderParcel(p)));
  } catch (err) {
    toast(err.message);
  }
}

function renderParcel(p) {
  const card = document.createElement('article');
  card.className = 'parcel' + (p.overdue ? ' overdue' : '');

  const top = document.createElement('div');
  top.className = 'parcel-top';
  top.innerHTML = `
    <span class="badge ${p.status}">${p.statusLabel}</span>
    <span class="courier">${escapeHtml(p.station || p.courier || '快递')}</span>
    <span class="parcel-time">${p.createdAt}</span>
  `;

  const meta = document.createElement('div');
  meta.className = 'parcel-meta';
  meta.innerHTML = `
    ${p.deadline ? `<span>期望 <b>${p.deadline}</b></span>` : ''}
    ${p.reward ? `<span>悬赏 <b>${escapeHtml(p.reward)}</b></span>` : ''}
  `;

  card.appendChild(top);
  card.appendChild(meta);

  if (p.pickupCode) {
    const codeRow = document.createElement('div');
    codeRow.className = 'code-row';
    codeRow.innerHTML = `<span class="muted">取件码</span><span class="code">${escapeHtml(p.pickupCode)}</span>`;
    card.appendChild(codeRow);
  }

  const foot = document.createElement('div');
  foot.className = 'parcel-foot';
  const isMine = p.ownerId === state.me.memberId;
  foot.innerHTML = `
    <span>${isMine ? '我发布的' : escapeHtml(p.ownerName) + ' 发布'}</span>
    ${p.takerName ? `<span>· ${escapeHtml(p.takerName)} 接单</span>` : ''}
    <span class="spacer"></span>
  `;

  if (p.status === 'OPEN' && !isMine) {
    foot.appendChild(actionButton('接单', () => act(p.id, 'take')));
  } else if (p.status === 'TAKEN' && p.takerId === state.me.memberId) {
    foot.appendChild(actionButton('我已取到', () => act(p.id, 'pick')));
  } else if (p.status === 'PICKED' && p.ownerId === state.me.memberId) {
    foot.appendChild(actionButton('已收到', () => act(p.id, 'deliver')));
  } else if (p.status === 'OPEN' && isMine) {
    foot.appendChild(actionButton('取消', () => act(p.id, 'cancel'), true));
  }

  card.appendChild(foot);
  return card;
}

function actionButton(text, onClick, secondary = false) {
  const btn = document.createElement('button');
  btn.className = 'small-btn' + (secondary ? ' secondary' : '');
  btn.textContent = text;
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    try {
      await onClick();
    } finally {
      btn.disabled = false;
    }
  });
  return btn;
}

async function act(parcelId, action) {
  try {
    await api(`/api/parcels/${parcelId}/${action}`, { method: 'POST' });
    toast('操作成功');
    await loadList();
    refreshBell();
  } catch (err) {
    toast(err.message);
  }
}

/* ---------------- 消息（站内提醒） ---------------- */

$('#bell').addEventListener('click', openNotices);
$('#noticeClose').addEventListener('click', () => $('#noticeSheet').classList.add('hidden'));
$('#noticeSheet').addEventListener('click', (e) => {
  if (e.target.id === 'noticeSheet') $('#noticeSheet').classList.add('hidden');
});

function renderBell(unread) {
  $('#bellDot').classList.toggle('hidden', unread === 0);
}

async function refreshBell() {
  try {
    const list = await api('/api/notices');
    renderBell(list.length);
  } catch {
    /* 没加入宿舍时不用管 */
  }
}

async function openNotices() {
  const box = $('#noticeList');
  box.innerHTML = '<div class="notice-empty">加载中…</div>';
  $('#noticeSheet').classList.remove('hidden');
  try {
    const list = await api('/api/notices');
    box.innerHTML = '';
    if (list.length === 0) {
      box.innerHTML = '<div class="notice-empty">还没有新消息</div>';
    }
    list.forEach((n) => {
      const item = document.createElement('div');
      item.className = 'notice-item';
      item.innerHTML = `${escapeHtml(n.text)}<time>${escapeHtml(n.at)}</time>`;
      box.appendChild(item);
    });
    // 看过就算已读
    await api('/api/notices/read', { method: 'POST' });
    renderBell(0);
  } catch (err) {
    box.innerHTML = `<div class="notice-empty">${escapeHtml(err.message)}</div>`;
  }
}

/* ---------------- 发布弹层 ---------------- */

$('#fab').addEventListener('click', () => $('#sheet').classList.remove('hidden'));
$('#sheetClose').addEventListener('click', closeSheet);
$('#sheet').addEventListener('click', (e) => {
  if (e.target.id === 'sheet') closeSheet();
});

function closeSheet() {
  $('#sheet').classList.add('hidden');
  $('#parcelForm').reset();
}

$('#parcelForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const body = {
    station: $('#station').value,
    pickupCode: $('#pickupCode').value.trim(),
    deadline: $('#deadline').value,          // datetime-local 直接给 yyyy-MM-ddTHH:mm
    reward: $('#reward').value.trim(),
  };
  try {
    await api('/api/parcels', { method: 'POST', body: JSON.stringify(body) });
    closeSheet();
    toast('发布成功');
    if (state.tab !== 'active') {
      document.querySelector('[data-tab="active"]').click();
    } else {
      await loadList();
    }
  } catch (err) {
    toast(err.message);
  }
});

/* ---------------- 小工具 ---------------- */

function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

boot();
