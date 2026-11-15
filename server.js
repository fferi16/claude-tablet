#!/usr/bin/env node
// Claude Tablet – böngészős, érintésbarát felület a Claude Code-hoz.
// Nincs külső függőség: csak Node.js (>=18) és a `claude` parancs kell.
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { spawn } = require('child_process');

const VERSION = '1.10.0';
const https = require('https');
const PORT = Number(process.env.PORT || 8765);
const HOST = process.env.HOST || '127.0.0.1';
const HOME = os.homedir();
const CLAUDE = process.env.CLAUDE_BIN || 'claude';
const PUB = path.join(__dirname, 'public');
const CFG_FILE = path.join(HOME, '.claude-tablet.json');
// A `claude` általában a /usr/local/bin-ben van – legyen mindig a PATH-on (pl. Termuxból indítva).
for (const d of ['/usr/local/bin', path.join(HOME, '.local', 'bin'), path.join(HOME, '.npm-global', 'bin'), path.join(__dirname, 'bin')])
  if (!(process.env.PATH || '').split(path.delimiter).includes(d)) process.env.PATH = (process.env.PATH ? process.env.PATH + path.delimiter : '') + d;

// A `claude` futtatható megkeresése (abszolút útvonal vagy PATH). null, ha nincs.
const WIN = process.platform === 'win32';
function findClaude() {
  const isExe = f => { try { fs.accessSync(f, WIN ? fs.constants.F_OK : fs.constants.X_OK); return fs.statSync(f).isFile(); } catch { return false; } };
  const exts = WIN ? ['.exe', '.cmd', ''] : [''];
  if (CLAUDE.includes('/') || CLAUDE.includes('\\')) return isExe(CLAUDE) ? CLAUDE : null;
  for (const d of (process.env.PATH || '').split(path.delimiter))
    for (const e of exts) if (d && isExe(path.join(d, CLAUDE + e))) return path.join(d, CLAUDE + e);
  return null;
}
function log(...a) { console.log(new Date().toISOString(), ...a); }
// ---- Hozzáférés-védelem ----
// Helyi mód (127.0.0.1, a tableten): az Android app egy saját titkot ad át a Termuxon keresztül
// (start-server.sh <titok> → ~/.claude-tablet/secret, csak a Termux olvashatja). Ha van titok, minden
// kérésnek tartalmaznia kell (X-CT-Secret fejléc vagy ct_token süti) – így a tableten lévő MÁS appok
// nem vezérelhetik a motort. Titok nélkül (régi app) a motor a régi módon, védelem nélkül fut.
// Hálózati mód (HOST=0.0.0.0, PC): párosító kód kell (a tablet appban egyszer kell megadni).
const LOCAL = HOST === '127.0.0.1' || HOST === 'localhost';
const CODE_ALPHA = '0123456789abcdefghjkmnpqrstvwxyz'; // 32 jel, félreolvasható i/l/o/u nélkül
function newCode(n) { let s = ''; for (const b of crypto.randomBytes(n)) s += CODE_ALPHA[b & 31]; return s; }
const TOKEN = LOCAL ? null
  : (process.env.TOKEN || (() => {
      const c = loadCfg();
      if (!c.token || c.token.length < 10) { c.token = newCode(10); saveCfg(c); } // ~50 bit; a régi rövid kódot lecseréli
      return c.token;
    })());
const APP_SECRET_FILE = process.env.CT_SECRET_FILE || '/data/data/com.termux/files/home/.claude-tablet/secret';
let secretCache = { m: -1, v: null };
function appSecret() {
  try {
    const st = fs.statSync(APP_SECRET_FILE);
    if (st.mtimeMs !== secretCache.m) secretCache = { m: st.mtimeMs, v: fs.readFileSync(APP_SECRET_FILE, 'utf8').trim() || null };
  } catch { secretCache = { m: -1, v: null }; }
  return secretCache.v;
}
function accessKey() { return LOCAL ? appSecret() : TOKEN; }
function sameStr(a, b) {
  a = Buffer.from(String(a)); b = Buffer.from(String(b));
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function getCookie(req, name) {
  for (const p of String(req.headers.cookie || '').split(';')) {
    const i = p.indexOf('=');
    if (i > 0 && p.slice(0, i).trim() === name) { try { return decodeURIComponent(p.slice(i + 1).trim()); } catch { return null; } }
  }
  return null;
}
function givenKey(req) { return req.headers['x-ct-secret'] || getCookie(req, 'ct_token'); }
// 'off' = nincs védelem beállítva, 'ok' = a kérés helyes kulcsot hozott, 'bad' = hiányzik/hibás
function authState(req) { const k = accessKey(); if (!k) return 'off'; const g = givenKey(req); return g && sameStr(g, k) ? 'ok' : 'bad'; }
// hibás kód/kulcs próbálkozások korlátozása IP-nként (percenként 10)
const authFails = new Map();
function tooManyFails(ip) { const f = authFails.get(ip); return !!f && f.n >= 10 && Date.now() - f.t < 60000; }
function noteFail(ip) { const now = Date.now(), f = authFails.get(ip); if (!f || now - f.t > 60000) authFails.set(ip, { n: 1, t: now }); else f.n++; }

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const runs = new Map(); // runId -> child process
const streams = new Set(); // nyitott /api/send válasz-streamek (emit függvények) – ide megy az app-indítás jelzése

// ---- App indítása a tableten az appon keresztül (kép a képben + alá nyílik a kért app) ----
const APP_ALIAS = {
  chrome: 'com.android.chrome', 'klóm': 'com.android.chrome', 'króm': 'com.android.chrome', bongeszo: 'com.android.chrome', 'böngésző': 'com.android.chrome',
  youtube: 'com.google.android.youtube', gmail: 'com.google.android.gm', maps: 'com.google.android.apps.maps', terkep: 'com.google.android.apps.maps', 'térkép': 'com.google.android.apps.maps',
  termux: 'com.termux', beallitasok: 'com.android.settings', 'beállítások': 'com.android.settings', settings: 'com.android.settings',
  playstore: 'com.android.vending', 'play': 'com.android.vending', naptar: 'com.google.android.calendar', 'naptár': 'com.google.android.calendar',
  drive: 'com.google.android.apps.docs', kamera: 'com.android.camera', camera: 'com.android.camera', galeria: 'com.miui.gallery', 'galéria': 'com.miui.gallery',
};
function resolveApp(a) {
  a = String(a || '').trim();
  if (/^[a-z][\w]*(\.[\w]+)+$/i.test(a)) return a;
  return APP_ALIAS[a.toLowerCase()] || APP_ALIAS[a.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')] || null;
}

function loadCfg() { try { return JSON.parse(fs.readFileSync(CFG_FILE, 'utf8')); } catch { return { recent: [] }; } }
function saveCfg(c) { try { fs.writeFileSync(CFG_FILE, JSON.stringify(c, null, 2)); } catch {} }

function json(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(obj));
}
function readBody(req, max = 5e6) {
  return new Promise((ok, fail) => {
    let b = ''; req.on('data', d => { b += d; if (b.length > max) req.destroy(); });
    req.on('end', () => { try { ok(b ? JSON.parse(b) : {}); } catch (e) { fail(e); } });
  });
}
function isDir(p) { try { return fs.statSync(p).isDirectory(); } catch { return false; } }
function projDir(cwd) { return path.join(HOME, '.claude', 'projects', cwd.replace(/[^a-zA-Z0-9]/g, '-')); }

function textOf(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) return content.filter(c => c.type === 'text').map(c => c.text).join('\n');
  return '';
}
function cleanUserText(t) {
  return t.replace(/<system-reminder>[\s\S]*?<\/system-reminder>/g, '').trim();
}

// ---- Munkamenetek (a Claude Code saját .jsonl fájljaiból) ----
function listSessions(cwd) {
  const dir = projDir(cwd);
  let files = [];
  try { files = fs.readdirSync(dir).filter(f => f.endsWith('.jsonl')); } catch { return []; }
  return files.map(f => {
    const fp = path.join(dir, f), st = fs.statSync(fp);
    let title = '', count = 0;
    try {
      const lines = fs.readFileSync(fp, 'utf8').split('\n');
      for (const l of lines) {
        if (!l.includes('"type":"user"')) continue;
        let o; try { o = JSON.parse(l); } catch { continue; }
        if (o.type !== 'user' || o.isMeta || o.isSidechain) continue;
        const t = cleanUserText(textOf(o.message?.content));
        if (!t || t.startsWith('<')) continue;
        count++; if (!title) title = t.slice(0, 90);
      }
    } catch {}
    return { id: f.slice(0, -6), title: title || '(üres)', mtime: st.mtimeMs, count };
  }).filter(s => s.count > 0 && !s.title.startsWith('[Lebegő Claude-gomb')).sort((a, b) => b.mtime - a.mtime).slice(0, 60);
}

function history(cwd, id) {
  const fp = path.join(projDir(cwd), id + '.jsonl');
  const out = [];
  let lines; try { lines = fs.readFileSync(fp, 'utf8').split('\n'); } catch { return out; }
  for (const l of lines) {
    let o; try { o = JSON.parse(l); } catch { continue; }
    if (o.isSidechain || o.isMeta) continue;
    const c = o.message?.content;
    if (o.type === 'user') {
      if (typeof c === 'string') { const t = cleanUserText(c); if (t && !t.startsWith('<')) out.push({ kind: 'user', text: t }); }
      else if (Array.isArray(c)) for (const b of c) {
        if (b.type === 'tool_result') out.push({ kind: 'result', id: b.tool_use_id, content: resultText(b.content), isError: !!b.is_error });
        else if (b.type === 'text') { const t = cleanUserText(b.text); if (t && !t.startsWith('<')) out.push({ kind: 'user', text: t }); }
      }
    } else if (o.type === 'assistant' && Array.isArray(c)) {
      for (const b of c) {
        if (b.type === 'text' && b.text.trim()) out.push({ kind: 'text', text: b.text });
        else if (b.type === 'tool_use') out.push({ kind: 'tool', id: b.id, name: b.name, input: b.input });
      }
    }
  }
  let last = null;
  for (let i = lines.length - 1; i >= 0 && !last; i--) {
    if (!lines[i].includes('"usage"')) continue;
    try { const o = JSON.parse(lines[i]); if (o.type === 'assistant' && !o.isSidechain && o.message?.usage) last = o; } catch {}
  }
  if (last) out.push({ kind: 'ctx', tokens: ctxTokens(last.message.usage), model: last.message.model, window: ctxWindow(last.message.model) });
  return out;
}
function ctxTokens(u) { return (u.input_tokens || 0) + (u.cache_creation_input_tokens || 0) + (u.cache_read_input_tokens || 0) + (u.output_tokens || 0); }
function ctxWindow(model) {
  const w = (loadCfg().ctxWin || {});
  if (model && w[model]) return w[model];
  return /\[1m\]/i.test(model || '') ? 1000000 : 200000;
}
// ---- 5 órás / heti limit (claude.ai előfizetés), 60 mp-es gyorsítótárral ----
let usageCache = { t: 0, data: null };
function fetchUsage() {
  if (Date.now() - usageCache.t < 60000 && usageCache.data) return Promise.resolve(usageCache.data);
  let tok;
  try { tok = JSON.parse(fs.readFileSync(path.join(HOME, '.claude', '.credentials.json'), 'utf8')).claudeAiOauth?.accessToken; } catch {}
  if (!tok) return Promise.resolve({ error: 'Nincs bejelentkezés' });
  return new Promise(ok => {
    const r = https.get('https://api.anthropic.com/api/oauth/usage', { headers: { Authorization: 'Bearer ' + tok, 'anthropic-beta': 'oauth-2025-04-20', 'User-Agent': 'claude-tablet/' + VERSION }, timeout: 15000 }, res => {
      let b = ''; res.on('data', d => b += d);
      res.on('end', () => {
        try {
          const j = JSON.parse(b);
          if (res.statusCode !== 200) return ok({ error: j.error?.message || 'HTTP ' + res.statusCode });
          const pick = x => x ? { pct: x.utilization, resets: x.resets_at } : null;
          usageCache = { t: Date.now(), data: { five: pick(j.five_hour), week: pick(j.seven_day) } };
          ok(usageCache.data);
        } catch (e) { ok({ error: e.message }); }
      });
    });
    r.on('timeout', () => r.destroy(new Error('időtúllépés')));
    r.on('error', e => ok(usageCache.data || { error: e.message }));
  });
}
function resultText(c) {
  if (typeof c === 'string') return c.slice(0, 20000);
  if (Array.isArray(c)) return c.map(x => x.type === 'text' ? x.text : `[${x.type}]`).join('\n').slice(0, 20000);
  return '';
}

// ---- Egy üzenet elküldése: `claude -p` futtatása, eredmény NDJSON-ként streamelve ----
function send(req, res, body, hooks = {}) {
  const cwd = body.cwd;
  if (!isDir(cwd)) return json(res, 400, { error: 'Nem létező mappa: ' + cwd });
  const args = ['-p', '--output-format', 'stream-json', '--verbose', '--include-partial-messages'];
  if (body.sessionId && UUID.test(body.sessionId)) args.push('--resume', body.sessionId);
  const modes = ['auto', 'acceptEdits', 'bypassPermissions', 'manual', 'dontAsk', 'plan'];
  if (modes.includes(body.mode)) args.push('--permission-mode', body.mode);
  if (body.model && /^[a-z0-9.\-\[\]]+$/i.test(body.model)) args.push('--model', body.model);

  const cfg = loadCfg();
  cfg.recent = [cwd, ...(cfg.recent || []).filter(p => p !== cwd)].slice(0, 12);
  saveCfg(cfg);

  const bin = findClaude();
  if (!bin) return json(res, 503, { error: 'A `claude` parancs nem található. Telepítsd a proot-ban: npm install -g @anthropic-ai/claude-code' });

  const runId = crypto.randomUUID();
  const t0 = Date.now();
  const child = spawn(bin, args, { cwd, env: { ...process.env, FORCE_COLOR: '0' }, shell: WIN && /\.cmd$/i.test(bin), windowsHide: true });
  runs.set(runId, child);
  log(`run start ${runId.slice(0, 8)} cwd=${cwd} session=${body.sessionId || 'new'} mode=${body.mode || '-'}`);
  child.stdin.on('error', () => {}); // ha a folyamat nem indult el, ne dőljön el a szerver (EPIPE)
  child.stdin.end(String(body.prompt || ''));

  if (!res.headersSent) res.writeHead(200, { 'Content-Type': 'application/x-ndjson; charset=utf-8', 'Cache-Control': 'no-store', 'X-Accel-Buffering': 'no' });
  const emit = o => { if (o.kind === 'session' && hooks.onSession) hooks.onSession(o.id); if (hooks.onEmit) hooks.onEmit(o); try { res.write(JSON.stringify(o) + '\n'); } catch {} };
  emit({ kind: 'run', runId });
  streams.add(emit);

  let buf = '', errBuf = '';
  child.stdout.on('data', d => {
    buf += d; let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i); buf = buf.slice(i + 1);
      if (!line.trim()) continue;
      let o; try { o = JSON.parse(line); } catch { continue; }
      translate(o, emit);
    }
  });
  child.stderr.on('data', d => { errBuf += d; });
  let finished = false;
  const finish = code => {
    if (finished) return; finished = true;
    runs.delete(runId);
    streams.delete(emit);
    if (buf.trim()) { try { translate(JSON.parse(buf), emit); } catch {} buf = ''; }
    if (code && errBuf.trim()) emit({ kind: 'error', text: errBuf.trim().slice(-3000) });
    emit({ kind: 'end', code });
    res.end();
    log(`run end   ${runId.slice(0, 8)} code=${code} ${((Date.now() - t0) / 1000).toFixed(1)}s`);
  };
  child.on('error', e => { emit({ kind: 'error', text: 'Nem indítható a claude: ' + e.message }); finish(-1); });
  child.on('close', code => finish(code));
  res.on('close', () => { /* a böngésző bezárása nem állítja le a futást */ });
}

function translate(o, emit) {
  if (o.parent_tool_use_id) return; // al-agent belső üzenetei – nem zsúfoljuk a felületet
  if (o.type === 'system' && o.subtype === 'init') return emit({ kind: 'session', id: o.session_id, model: o.model });
  if (o.type === 'stream_event') {
    const e = o.event;
    if (e.type === 'content_block_delta' && e.delta?.type === 'text_delta') emit({ kind: 'delta', text: e.delta.text });
    else if (e.type === 'content_block_start' && e.content_block?.type === 'thinking') emit({ kind: 'thinking' });
    return;
  }
  if (o.type === 'assistant') {
    const us = o.message?.usage;
    if (us) emit({ kind: 'ctx', tokens: ctxTokens(us), model: o.message.model });
    for (const b of o.message?.content || []) {
      if (b.type === 'text') emit({ kind: 'text', text: b.text });
      else if (b.type === 'tool_use') emit({ kind: 'tool', id: b.id, name: b.name, input: b.input });
    }
    return;
  }
  if (o.type === 'user' && Array.isArray(o.message?.content)) {
    for (const b of o.message.content) if (b.type === 'tool_result')
      emit({ kind: 'result', id: b.tool_use_id, content: resultText(b.content), isError: !!b.is_error });
    return;
  }
  if (o.type === 'result' && o.modelUsage) {
    let win = 0, model = null;
    for (const [m, v] of Object.entries(o.modelUsage)) if ((v.contextWindow || 0) > win) { win = v.contextWindow; model = m; }
    if (win) { const c = loadCfg(); c.ctxWin = { ...(c.ctxWin || {}), [model]: win }; saveCfg(c); emit({ kind: 'ctxwin', window: win, model }); }
  }
  if (o.type === 'result') emit({ kind: 'done', cost: o.total_cost_usd, ms: o.duration_ms, isError: o.is_error, session: o.session_id });
}

// ---- Lebegő gomb (másik app fölött): opcionális képernyőkép + rövid, felolvasható válasz ----
const SHOT_DIR = path.join(HOME, 'tablet-shots');
const TABLET = path.join(HOME, 'bin', 'tablet');
function takeShot(file) {
  return new Promise(ok => {
    if (!fs.existsSync(TABLET)) return ok(false);
    const c = spawn(TABLET, ['shot', file], { stdio: 'ignore', env: { ...process.env, CT_NO_HIDE: '1' } });
    const t = setTimeout(() => c.kill('SIGKILL'), 20000);
    c.on('error', () => { clearTimeout(t); ok(false); });
    c.on('close', code => { clearTimeout(t); ok(code === 0 && fs.existsSync(file) && fs.statSync(file).size > 1000); });
  });
}
// Könnyű memória: rövid jegyzetfájl (mindig bekerül) + az utolsó néhány kérdés-válasz kivonata (új beszélgetésnél)
const BUBBLE_MEM = path.join(HOME, '.claude-tablet-bubble.md');
const clip = (t, n) => { t = String(t || '').replace(/\s+/g, ' ').trim(); return t.length > n ? t.slice(0, n) + '…' : t; };
function bubbleMemory() {
  let notes = '';
  try { notes = fs.readFileSync(BUBBLE_MEM, 'utf8').split('\n').filter(l => l.trim()).slice(-30).join('\n'); } catch {}
  return notes;
}
async function bubble(req, res, body) {
  const cfg = loadCfg();
  const text = String(body.text || '').trim();
  if (!text) return json(res, 400, { error: 'Üres kérés' });
  // 20 perc tétlenség után új beszélgetés
  let sid = cfg.bubbleSession;
  if (body.newSession || !sid || Date.now() - (cfg.bubbleAt || 0) > 20 * 60000) sid = null;
  res.writeHead(200, { 'Content-Type': 'application/x-ndjson; charset=utf-8', 'Cache-Control': 'no-store', 'X-Accel-Buffering': 'no' });
  let shot = null;
  if (body.screen) {
    fs.mkdirSync(SHOT_DIR, { recursive: true });
    const f = path.join(SHOT_DIR, 'bubble-' + Date.now() + '.png');
    if (await takeShot(f)) shot = f;
    // régi buborék-képek takarítása (az utolsó 10 marad)
    try { fs.readdirSync(SHOT_DIR).filter(n => n.startsWith('bubble-')).sort().slice(0, -10).forEach(n => fs.rmSync(path.join(SHOT_DIR, n))); } catch {}
  }
  try { res.write(JSON.stringify({ kind: 'shot', ok: !!shot }) + '\n'); } catch {}
  const pre = '[Lebegő Claude-gomb: a felhasználó a tableten épp egy másik appot használ, és onnan kérdez (általában diktálva). ' +
    'Válaszolj röviden, a felhasználó nyelvén (amin kérdez), felolvasásra alkalmasan (kevés markdown, nincs táblázat/kódblokk, hacsak nem kéri). ' +
    (shot ? `A kérés pillanatában készült képernyőkép: ${shot} – ha a kérés a képernyőre vonatkozik, nézd meg Read-dal. ` :
      (body.screen ? 'Képernyőképet most nem sikerült készíteni (ADB nem elérhető). ' : '')) +
    'A tabletet a `tablet` paranccsal vezérelheted: `tablet text` (a képernyő szövegei koordinátákkal – olcsóbb, mint a kép), `tablet clicktext "Felirat"`, tap X Y, swipe, type "szöveg" (ékezet is), key BACK|HOME|ENTER, open app, shot. Csak akkor nyúlj hozzá, ha a felhasználó cselekvést kér.]\n\n';
  const notes = bubbleMemory();
  const log = (cfg.bubbleLog || []).slice(-5);
  const mem = 'Lebegő-gomb memória – jegyzetfájl: ' + BUBBLE_MEM + '. Ha a felhasználó kéri, hogy jegyezz meg valamit, vagy tartós, hasznos tényt/szokást tudsz meg róla, ' +
    'írj hozzá EGY rövid sort (max ~120 karakter, dátummal), elavultat törölj; legfeljebb 30 sor. Ne jegyezz fel egyszeri, átmeneti dolgot. ' +
    'A teljes fő memóriát (MEMORY.md) a lebegő kérésekhez ne bővítsd.\n' +
    (notes ? 'Jegyzetek:\n' + notes + '\n' : '(A jegyzetfájl még üres.)\n') +
    (!sid && log.length ? 'Legutóbbi lebegő kérések (régebbi beszélgetésekből):\n' + log.map(x => `- ${x.t}: „${x.q}” → ${x.a}`).join('\n') + '\n' : '');
  const save = id => { const c = loadCfg(); c.bubbleSession = id; c.bubbleAt = Date.now(); saveCfg(c); };
  let answer = '';
  const onEmit = o => {
    if (o.kind === 'text') answer = o.text;
    if (o.kind === 'done' && answer) {
      const c = loadCfg();
      const t = new Date(); const ts = `${t.getMonth() + 1}.${t.getDate()}. ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`;
      c.bubbleLog = [...(c.bubbleLog || []), { t: ts, q: clip(text.replace(/\n\nKijelölt szöveg:[\s\S]*/, ' (+kijelölt szöveg)'), 120), a: clip(answer, 160) }].slice(-5);
      saveCfg(c);
    }
  };
  if (sid) save(sid);
  send(req, res, { cwd: HOME, sessionId: sid, prompt: pre.replace(/\]\n\n$/, '\n' + mem + ']\n\n') + text, mode: cfg.bubbleMode || 'auto' }, { onSession: save, onEmit });
}

// ---- Ügynök: az app akadálymentességi szolgáltatása (képernyőkép, koppintás, gépelés ADB nélkül) ----
// Az app nyitva tart egy GET /api/agent/listen streamet; a parancsok ezen mennek, az eredmény POST /api/agent/result-tal jön vissza.
let agent = null;
const agentWait = new Map();
function agentListen(req, res) {
  res.writeHead(200, { 'Content-Type': 'application/x-ndjson; charset=utf-8', 'Cache-Control': 'no-store', 'X-Accel-Buffering': 'no' });
  const me = { emit: o => { try { res.write(JSON.stringify(o) + '\n'); } catch {} } };
  agent = me; log('ügynök csatlakozott');
  me.emit({ cmd: 'hello', version: VERSION });
  const hb = setInterval(() => me.emit({ cmd: 'ping' }), 20000);
  res.on('close', () => { clearInterval(hb); if (agent === me) { agent = null; log('ügynök lecsatlakozott'); } });
}
function agentDo(cmd, timeout = 20000) {
  return new Promise(ok => {
    if (!agent) return ok({ ok: false, noAgent: true, error: 'Az app akadálymentességi szolgáltatása nem fut' });
    const id = crypto.randomBytes(6).toString('hex');
    const t = setTimeout(() => { agentWait.delete(id); ok({ ok: false, error: 'Az app nem válaszolt időben' }); }, timeout);
    agentWait.set(id, r => { clearTimeout(t); ok(r); });
    agent.emit({ ...cmd, id });
  });
}
async function agentHttp(req, res) {
  const b = await readBody(req);
  const r = await agentDo(b);
  if (r.png) {
    const f = inHome(b.file) || path.join(SHOT_DIR, 'now.png');
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, Buffer.from(r.png, 'base64'));
    delete r.png; r.file = f;
  }
  json(res, r.noAgent ? 503 : 200, r);
}

// ---- Mappák ----
function projects() {
  const cfg = loadCfg();
  let dirs = [];
  try {
    dirs = fs.readdirSync(HOME, { withFileTypes: true })
      .filter(d => d.isDirectory() && !d.name.startsWith('.'))
      .map(d => path.join(HOME, d.name));
  } catch {}
  const recent = (cfg.recent || []).filter(isDir);
  return { home: HOME, recent, dirs: [HOME, ...dirs.filter(d => !recent.includes(d))] };
}
function browse(p) {
  p = path.resolve(p || HOME);
  if (!isDir(p)) return { error: 'Nem mappa' };
  let entries = [];
  try {
    entries = fs.readdirSync(p, { withFileTypes: true })
      .filter(d => d.isDirectory() && !d.name.startsWith('.')).map(d => d.name).sort();
  } catch {}
  return { path: p, parent: path.dirname(p), entries };
}

// ---- HTTP ----
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif', '.webp': 'image/webp', '.bmp': 'image/bmp', '.heic': 'image/heic', '.pdf': 'application/pdf',
  '.txt': 'text/plain; charset=utf-8', '.md': 'text/plain; charset=utf-8', '.csv': 'text/plain; charset=utf-8', '.mp4': 'video/mp4', '.mp3': 'audio/mpeg' };

// ---- Motor újraindítása: Termux-oldali szkript (az Android `am` paranccsal), megvárja a futó kéréseket ----
const TERMUX_BIN = '/data/data/com.termux/files/usr/bin';
const RESTART_SH = '/data/data/com.termux/files/home/.claude-tablet/restart-server.sh';
function requestRestart() {
  if (!fs.existsSync(RESTART_SH) || !fs.existsSync(TERMUX_BIN + '/am')) return { ok: false, error: 'Az újraindító szkript nem található' };
  const c = spawn(TERMUX_BIN + '/am', ['startservice', '--user', '0', '-n', 'com.termux/com.termux.app.RunCommandService',
    '-a', 'com.termux.RUN_COMMAND', '--es', 'com.termux.RUN_COMMAND_PATH', RESTART_SH, '--ez', 'com.termux.RUN_COMMAND_BACKGROUND', 'true'],
    { env: { ...process.env, PATH: TERMUX_BIN + ':' + process.env.PATH }, stdio: 'ignore', detached: true });
  c.on('error', e => log('restart hiba: ' + e.message)); c.unref();
  log('újraindítás kérve');
  return { ok: true, waiting: runs.size, pid: process.pid };
}

// ---- Fájlok: feltöltés (csatolmány), listázás, kiszolgálás ----
const MAX_UPLOAD = 40 * 1024 * 1024;
function inHome(p) { p = path.resolve(p || ''); return (p === HOME || p.startsWith(HOME + '/')) ? p : null; }
function safeName(n) {
  n = path.basename(String(n || 'fajl')).normalize('NFC').replace(/[^\p{L}\p{N}._ -]+/gu, '_').replace(/^\.+/, '').slice(-90);
  return n || 'fajl';
}
function upload(req, res, u) {
  const cwd = u.searchParams.get('cwd');
  if (!isDir(cwd)) return json(res, 400, { error: 'Nem létező mappa' });
  const dir = path.join(cwd, '.claude-uploads');
  fs.mkdirSync(dir, { recursive: true });
  const gi = path.join(dir, '.gitignore'); if (!fs.existsSync(gi)) fs.writeFileSync(gi, '*\n');
  const stamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  const fp = path.join(dir, stamp + '-' + crypto.randomBytes(2).toString('hex') + '-' + safeName(u.searchParams.get('name')));
  const out = fs.createWriteStream(fp); let size = 0, failed = false;
  req.on('data', d => { size += d.length; if (size > MAX_UPLOAD && !failed) { failed = true; out.destroy(); fs.rm(fp, () => {}); json(res, 413, { error: 'Túl nagy fájl (max 40 MB)' }); req.destroy(); } });
  req.pipe(out);
  out.on('finish', () => { if (!failed) { log(`upload ${fp} ${size}B`); json(res, 200, { path: fp, size }); } });
  out.on('error', e => { if (!failed) { failed = true; json(res, 500, { error: e.message }); } });
}
function listFiles(p, hidden) {
  p = inHome(p) || HOME;
  if (!isDir(p)) return { error: 'Nem mappa' };
  let entries = [];
  try {
    entries = fs.readdirSync(p, { withFileTypes: true })
      .filter(d => hidden || !d.name.startsWith('.'))
      .map(d => {
        let st = null; try { st = fs.statSync(path.join(p, d.name)); } catch {}
        return { name: d.name, dir: st ? st.isDirectory() : d.isDirectory(), size: st?.size || 0, mtime: st?.mtimeMs || 0 };
      })
      .sort((a, b) => (b.dir - a.dir) || a.name.localeCompare(b.name, 'hu'));
  } catch (e) { return { error: e.message }; }
  return { path: p, parent: p === HOME ? null : path.dirname(p), entries: entries.slice(0, 1000) };
}
function serveFile(req, res, u) {
  const fp = inHome(u.searchParams.get('path'));
  let st; try { st = fp && fs.statSync(fp); } catch {}
  if (!st || !st.isFile()) { res.writeHead(404); return res.end('404'); }
  const ext = path.extname(fp).toLowerCase();
  let type = MIME[ext];
  if (!type) { // ismeretlen: ha szövegnek tűnik, text/plain
    const fd = fs.openSync(fp, 'r'), b = Buffer.alloc(Math.min(4096, st.size)); fs.readSync(fd, b, 0, b.length, 0); fs.closeSync(fd);
    type = b.includes(0) ? 'application/octet-stream' : 'text/plain; charset=utf-8';
  }
  const h = { 'Content-Type': type, 'Content-Length': st.size, 'Cache-Control': 'no-cache' };
  if (u.searchParams.get('dl')) h['Content-Disposition'] = `attachment; filename*=UTF-8''${encodeURIComponent(path.basename(fp))}`;
  res.writeHead(200, h);
  fs.createReadStream(fp).pipe(res);
}

function deny(res, u, code, msg) {
  if (u.pathname.startsWith('/api/')) return json(res, code, { error: msg, auth: code === 401 });
  res.writeHead(code, { 'Content-Type': 'text/plain; charset=utf-8' }); res.end(msg);
}
/** true = mehet tovább; false = a választ már elküldte (elutasítás vagy átirányítás) */
function guard(req, res, u) {
  const host = String(req.headers.host || '');
  // 1) Host: helyi módban csak 127.0.0.1 / localhost (DNS-rebinding ellen)
  if (LOCAL && !['127.0.0.1', 'localhost'].includes(host.replace(/:\d+$/, ''))) { deny(res, u, 403, 'Tiltott Host fejléc'); return false; }
  // 2) Idegen weboldal nem küldhet kérést: Origin csak a saját címünk lehet, és a böngésző
  //    Sec-Fetch-Site jelzése sem lehet „más oldalról” (a válasz nélküli „egyszerű” POST-ok ellen is véd)
  const origin = req.headers.origin;
  if (origin !== undefined && origin !== 'http://' + host) { deny(res, u, 403, 'Idegen eredetű kérés'); return false; }
  const sfs = req.headers['sec-fetch-site'];
  if (u.pathname.startsWith('/api/') && sfs && sfs !== 'same-origin' && sfs !== 'none') { deny(res, u, 403, 'Idegen eredetű kérés'); return false; }
  // 3) Kulcs / párosító kód
  if (u.pathname === '/api/health') return true; // állapot (titkot nem ad ki); az indító szkriptek használják
  const key = accessKey(), ip = req.socket.remoteAddress || '?';
  if (u.searchParams.has('token')) { // belépő link: ?token=... → süti, majd átirányítás a token nélküli címre
    const t = u.searchParams.get('token'); u.searchParams.delete('token');
    const rest = u.searchParams.toString(), loc = '/' + (rest ? '?' + rest : '');
    if (key && tooManyFails(ip)) { deny(res, u, 429, 'Túl sok hibás próbálkozás – várj egy percet.'); return false; }
    if (key && !sameStr(t, key)) { noteFail(ip); deny(res, u, 401, 'Hibás kód / kulcs.'); return false; }
    const h = { Location: loc };
    if (key) h['Set-Cookie'] = `ct_token=${encodeURIComponent(key)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=31536000`;
    res.writeHead(302, h); res.end(); return false;
  }
  if (!key) return true; // helyi mód, az app még nem adott titkot (régi app) – visszafelé kompatibilis
  const g = givenKey(req);
  if (g && sameStr(g, key)) return true;
  if (g) noteFail(ip);
  deny(res, u, 401, LOCAL ? 'Hiányzó vagy hibás kulcs – nyisd meg a Claude Tablet appot.' : 'Hiányzó kód – nyisd meg a szerver által kiírt linket.');
  return false;
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://x');
  if (!guard(req, res, u)) return;
  try {
    if (u.pathname === '/api/health') return json(res, 200, { ok: true, version: VERSION, pid: process.pid, claude: !!findClaude(), login: fs.existsSync(path.join(HOME, '.claude', '.credentials.json')) || !!process.env.ANTHROPIC_API_KEY, runs: runs.size, agent: !!agent, auth: authState(req) });
    if (u.pathname === '/api/projects') return json(res, 200, projects());
    if (u.pathname === '/api/browse') return json(res, 200, browse(u.searchParams.get('path')));
    if (u.pathname === '/api/sessions') return json(res, 200, listSessions(u.searchParams.get('cwd') || HOME));
    if (u.pathname === '/api/history') {
      const id = u.searchParams.get('id');
      if (!UUID.test(id || '')) return json(res, 400, { error: 'rossz id' });
      return json(res, 200, history(u.searchParams.get('cwd') || HOME, id));
    }
    if (u.pathname === '/api/mkdir' && req.method === 'POST') {
      const b = await readBody(req); const p = path.resolve(b.path || '');
      if (!p.startsWith(HOME)) return json(res, 400, { error: 'Csak a saját mappádon belül' });
      fs.mkdirSync(p, { recursive: true }); return json(res, 200, { path: p });
    }
    if (u.pathname === '/api/usage') { if (u.searchParams.get('fresh')) usageCache.t = 0; return json(res, 200, await fetchUsage()); }
    if (u.pathname === '/api/restart' && req.method === 'POST') return json(res, 200, requestRestart());
    if (u.pathname === '/api/launch' && req.method === 'POST') {
      const b = await readBody(req); const pkg = resolveApp(b.app);
      if (!pkg) return json(res, 400, { error: 'Ismeretlen app: ' + b.app + ' (adj meg csomagnevet, pl. com.android.chrome)' });
      for (const em of streams) em({ kind: 'launch', pkg, label: String(b.app || pkg).slice(0, 40) });
      log(`launch ${pkg} -> ${streams.size} kliens`);
      return json(res, 200, { ok: true, pkg, delivered: streams.size });
    }
    if (u.pathname === '/api/agent/listen') return agentListen(req, res);
    if (u.pathname === '/api/agent/result' && req.method === 'POST') {
      const b = await readBody(req, 40e6); const w = agentWait.get(b.id);
      if (w) { agentWait.delete(b.id); w(b); }
      return json(res, 200, { ok: !!w });
    }
    if (u.pathname === '/api/agent/do' && req.method === 'POST') return agentHttp(req, res);
    if (u.pathname === '/api/bubble' && req.method === 'POST') return bubble(req, res, await readBody(req));
    // a `tablet shot` ezzel rejti el a lebegő gombot a képernyőkép idejére
    if (u.pathname === '/api/overlay' && req.method === 'POST') {
      const show = u.searchParams.get('show') === '1';
      for (const em of streams) em({ kind: 'overlay', show });
      return json(res, 200, { ok: true, delivered: streams.size });
    }
    if (u.pathname === '/api/upload' && req.method === 'POST') return upload(req, res, u);
    if (u.pathname === '/api/files') return json(res, 200, listFiles(u.searchParams.get('path'), u.searchParams.get('hidden') === '1'));
    if (u.pathname === '/api/file') return serveFile(req, res, u);
    if (u.pathname === '/api/send' && req.method === 'POST') return send(req, res, await readBody(req));
    if (u.pathname === '/api/stop' && req.method === 'POST') {
      const b = await readBody(req); const c = runs.get(b.runId);
      if (c) { c.kill('SIGINT'); setTimeout(() => { if (!c.killed || c.exitCode === null) c.kill('SIGKILL'); }, 3000); }
      return json(res, 200, { ok: !!c });
    }
    // statikus fájlok
    let fp = path.join(PUB, u.pathname === '/' ? 'index.html' : path.normalize(u.pathname));
    if (!fp.startsWith(PUB) || !fs.existsSync(fp) || fs.statSync(fp).isDirectory()) { res.writeHead(404); return res.end('404'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(fp)] || 'application/octet-stream' });
    fs.createReadStream(fp).pipe(res);
  } catch (e) { json(res, 500, { error: String(e.message || e) }); }
});

server.on('error', e => {
  log(e.code === 'EADDRINUSE' ? `A ${PORT}-es port foglalt (már fut egy motor?)` : 'Szerverhiba: ' + e.message);
  process.exit(1);
});
for (const sig of ['SIGTERM', 'SIGINT']) process.on(sig, () => {
  log(`leállítás (${sig}), futó: ${runs.size}`);
  for (const c of runs.values()) { try { c.kill('SIGTERM'); } catch {} }
  process.exit(0);
});
if (!findClaude()) log('FIGYELEM: a `claude` parancs nem található a PATH-on – üzenetküldés nem fog menni.');

server.listen(PORT, HOST, () => {
  const url = `http://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}/` + (TOKEN ? `?token=${TOKEN}` : '');
  log(`Claude Tablet ${VERSION} fut – http://${HOST}:${PORT}/`);
  console.log('\n  Claude Tablet fut ✔');
  console.log('  Nyisd meg a böngészőben:  ' + url);
  if (TOKEN) {
    for (const ifs of Object.values(os.networkInterfaces())) for (const i of ifs || [])
      if (i.family === 'IPv4' && !i.internal) console.log(`  Más eszközről:            http://${i.address}:${PORT}/?token=${TOKEN}\n  Claude Tablet app → Menü → ＋ Gép:  cím ${i.address}:${PORT}   kód ${TOKEN}`);
  }
  console.log('  Leállítás: Ctrl+C\n');
});
