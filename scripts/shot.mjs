/**
 * 用 Edge 的 DevTools 协议截图（带 cookie、带手机视口）。
 *
 * 用法：
 *   node shot.mjs <url> <out.png> [cookieValue] [width] [height]
 *
 * 依赖：Node 22+ 自带的全局 WebSocket 与 fetch，不需要装任何包。
 */
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const [, , url, out, cookieValue, widthArg, heightArg] = process.argv;
const width = Number(widthArg || 430);
const height = Number(heightArg || 932);

const port = 9223;
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-shot-'));

const child = spawn(EDGE, [
  '--headless=new',
  '--disable-gpu',
  '--hide-scrollbars',
  '--no-first-run',
  '--no-default-browser-check',
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${profile}`,
  'about:blank',
], { stdio: 'ignore' });

async function getWsUrl() {
  for (let i = 0; i < 50; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/list`);
      const targets = await res.json();
      const page = targets.find((t) => t.type === 'page' && t.webSocketDebuggerUrl);
      if (page) return page.webSocketDebuggerUrl;
    } catch {
      /* 还没起来 */
    }
    await sleep(200);
  }
  throw new Error('Edge 调试端口没有起来');
}

const ws = new WebSocket(await getWsUrl());
await new Promise((resolve) => ws.addEventListener('open', resolve, { once: true }));

let nextId = 1;
const pending = new Map();
ws.addEventListener('message', (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
  }
});

function send(method, params = {}) {
  const id = nextId++;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

await send('Page.enable');
await send('Runtime.enable');
await send('Emulation.setDeviceMetricsOverride', {
  width, height, deviceScaleFactor: 2, mobile: true,
});

if (cookieValue) {
  const host = new URL(url).hostname;   // cookie 要落在截图目标域名上，不能写死 127.0.0.1
  await send('Network.enable');
  await send('Network.setCookie', {
    name: 'pm_member',
    value: String(cookieValue),
    domain: host,
    path: '/',
    httpOnly: true,
  });
}

await send('Page.navigate', { url });
await sleep(1200);   // 等前端 fetch /api/me 回来并渲染

const shot = await send('Page.captureScreenshot', { format: 'png' });
fs.writeFileSync(out, Buffer.from(shot.data, 'base64'));
console.log('saved', out, width + 'x' + height);

ws.close();
child.kill();
await sleep(300);
try { fs.rmSync(profile, { recursive: true, force: true }); } catch { /* ignore */ }
process.exit(0);
