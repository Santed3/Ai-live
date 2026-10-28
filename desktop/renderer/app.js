const $ = (id) => document.getElementById(id);
const chat = $('chat'), inp = $('inp');
let busy = false, speakOn = false, convo = false, statusEl = null;

// ---------------------------------------------------------------- helpers
function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
function inline(t) {
  return esc(t)
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<b>$1</b>')
    .replace(/(https?:\/\/[^\s<)]+)/g, '<a data-href="$1">$1</a>');
}
// Small, safe markdown subset: paragraphs, lists, code blocks, bold, inline code, links.
function md(text) {
  const out = []; let list = null;
  const flush = () => { if (list) { out.push('</' + list + '>'); list = null; } };
  const parts = String(text).split(/```/);
  parts.forEach((part, i) => {
    if (i % 2) { flush(); out.push('<pre><code>' + esc(part.replace(/^\w*\n/, '').replace(/\n$/, '')) + '</code></pre>'); return; }
    for (const line of part.split('\n')) {
      let m;
      if ((m = line.match(/^\s*[-*•]\s+(.*)/))) { if (list !== 'ul') { flush(); out.push('<ul>'); list = 'ul'; } out.push('<li>' + inline(m[1]) + '</li>'); }
      else if ((m = line.match(/^\s*\d+[.)]\s+(.*)/))) { if (list !== 'ol') { flush(); out.push('<ol>'); list = 'ol'; } out.push('<li>' + inline(m[1]) + '</li>'); }
      else if (!line.trim()) flush();
      else { flush(); out.push('<p>' + inline(line.replace(/^#+\s*/, '')) + '</p>'); }
    }
  });
  flush();
  return out.join('');
}
chat.addEventListener('click', (e) => { const a = e.target.closest('a[data-href]'); if (a) api.openLink(a.dataset.href); });
function addMsg(user, text) {
  const d = document.createElement('div');
  d.className = 'm ' + (user ? 'u' : 'a');
  if (user) d.textContent = String(text); else d.innerHTML = md(text);
  chat.appendChild(d);
  chat.scrollTop = chat.scrollHeight;
  return d;
}
let live = null, liveText = '';
api.onDelta((t) => {
  clearStatus(); clearDots();
  if (!live) { live = addMsg(false, ''); liveText = ''; }
  liveText += t;
  live.innerHTML = md(liveText);
  chat.scrollTop = chat.scrollHeight;
});
api.onDeltaReset(() => { if (live) { live.remove(); live = null; liveText = ''; } });
let dotsEl = null;
function showDots() { if (dotsEl) return; dotsEl = document.createElement('div'); dotsEl.className = 'dots'; dotsEl.innerHTML = '<i></i><i></i><i></i>'; chat.appendChild(dotsEl); chat.scrollTop = chat.scrollHeight; }
function clearDots() { if (dotsEl) { dotsEl.remove(); dotsEl = null; } }
function setStatus(t) {
  clearDots();
  if (!statusEl) { statusEl = document.createElement('div'); statusEl.className = 's'; chat.appendChild(statusEl); }
  statusEl.textContent = t;
  chat.scrollTop = chat.scrollHeight;
}
function clearStatus() { if (statusEl) { statusEl.remove(); statusEl = null; } }

// ---------------------------------------------------------------- chips
const MAIL = ['outlook', 'olk', 'thunderbird', 'hxoutlook'];
const CHAT = ['whatsapp', 'telegram', 'discord', 'slack', 'teams', 'ms-teams', 'signal', 'messenger'];
const WEB = ['chrome', 'msedge', 'firefox', 'brave', 'opera', 'vivaldi'];
function chipsFor(title, proc) {
  const t = (title || '').toLowerCase(), p = (proc || '').toLowerCase(), out = [];
  const isMail = MAIL.includes(p) || /gmail|inbox|outlook|mail/.test(t);
  const isChat = CHAT.includes(p) || /whatsapp|messenger|telegram|discord|slack|teams/.test(t);
  const isWeb = WEB.includes(p);
  if (isMail) out.push(['Svara på mejl', null, true], ['Sammanfatta mejlet', 'Sammanfatta mejlet på skärmen i några punkter och säg vad som förväntas av mig.'], ['Skriv nytt mejl', 'Hjälp mig skriva ett nytt mejl. Fråga vad det ska handla om och till vem.']);
  else if (isChat) out.push(['Föreslå svar', null, true], ['Sammanfatta chatten', 'Sammanfatta konversationen på skärmen kort.'], ['Översätt', 'Översätt de senaste meddelandena på skärmen till svenska (eller engelska om de redan är på svenska).']);
  else if (isWeb) out.push(['Sammanfatta sidan', 'Sammanfatta sidan på skärmen i korta punkter.'], ['Sök mer om detta', 'Sök på webben efter mer information om ämnet på skärmen och sammanfatta vad du hittar.'], ['Förklara', 'Förklara enkelt det som står på skärmen.']);
  out.push(['Svara på frågan', 'Svara på frågan som syns på skärmen. Ge svaret tydligt.'], ['Sök på Google', 'Sök på webben efter svaret på frågan på skärmen och förklara det kort.'],
    ['Förklara skärmen', 'Förklara kort vad som visas på skärmen.'], ['Sammanfatta', 'Sammanfatta texten på skärmen kort.'],
    ['Översätt', 'Översätt texten på skärmen till svenska (eller engelska om den redan är svenska).'], ['Skriv text', 'Hjälp mig skriva en text utifrån det som visas på skärmen. Fråga om något saknas.'],
    ['Fixa', 'Hitta fel i texten på skärmen (stavning, fakta, logik) och föreslå rättelser.']);
  const seen = new Set();
  return out.filter((c) => (seen.has(c[0]) ? false : seen.add(c[0]))).slice(0, 7);
}
async function refreshChips() {
  const info = await api.chips();
  const box = $('chips');
  box.textContent = '';
  for (const [label, prompt, reply] of chipsFor(info.title, info.proc)) {
    const b = document.createElement('button');
    b.textContent = label;
    b.onclick = () => (reply ? makeReply() : send(prompt, false));
    box.appendChild(b);
  }
}

// ---------------------------------------------------------------- chat
async function send(text, voice) {
  text = String(text || '').trim();
  if (!text || busy) return;
  busy = true;
  addMsg(true, text);
  live = null; liveText = '';
  showDots();
  try {
    const r = await api.chat(text, voice);
    clearStatus(); clearDots();
    if (live && r.ok) { live.innerHTML = md(r.reply); live = null; } else { if (live) { live.remove(); live = null; } addMsg(false, r.ok ? r.reply : 'Fel: ' + r.error); }
    if (r.ok && (speakOn || voice)) speak(r.reply);
  } catch (e) { clearStatus(); clearDots(); addMsg(false, 'Fel: ' + e.message); }
  busy = false;
}
api.onStatus(setStatus);
$('go').onclick = () => { const t = inp.value; inp.value = ''; send(t, false); };
inp.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('go').click(); }
});
$('new').onclick = async () => { await api.newChat(); chat.textContent = ''; $('draftcard').classList.remove('show'); addMsg(false, 'Ny konversation. Vad vill du göra?'); refreshChips(); };
$('hide').onclick = () => api.setMode('menu');
$('quit').onclick = () => api.setMode('bar');

// ------------------------------------------------------- smart reply card
async function makeReply() {
  if (busy) return;
  busy = true; setStatus('Läser konversationen och skriver ett förslag…');
  const r = await api.reply();
  clearStatus(); busy = false;
  if (r.ok) { $('draft').value = r.text; $('draftcard').classList.add('show'); } else addMsg(false, 'Fel: ' + r.error);
}
const TOOLS = [
  ['Kortare', 'Gör texten kortare men behåll innebörden.'], ['Professionell', 'Skriv om i en professionell, artig ton.'],
  ['Vänligare', 'Skriv om i en varmare, vänligare ton.'], ['Översätt', 'Översätt till engelska. Om texten redan är engelska: översätt till svenska.'],
  ['Skriv om', 'Skriv om med annan formulering men samma innebörd.'], ['Längre', 'Gör texten något mer utförlig och tydlig.'],
];
for (const [label, instr] of TOOLS) {
  const b = document.createElement('button');
  b.textContent = label;
  b.onclick = async () => {
    const cur = $('draft').value; if (!cur.trim() || busy) return;
    busy = true; setStatus('Skriver om…');
    const r = await api.transform(instr, cur);
    clearStatus(); busy = false;
    if (r.ok) $('draft').value = r.text; else addMsg(false, 'Fel: ' + r.error);
  };
  $('tools').appendChild(b);
}
$('copy').onclick = async () => { await api.copy($('draft').value); setStatus('Kopierat.'); setTimeout(clearStatus, 1500); };
async function insert(sendIt) {
  const t = $('draft').value; if (!t.trim()) return;
  const r = await api.insert(t, sendIt);
  setStatus(r.ok ? r.text : r.error); setTimeout(clearStatus, 2500);
}
$('insert').onclick = () => insert(false);
$('sendd').onclick = () => insert(true);
$('closed').onclick = () => $('draftcard').classList.remove('show');

// ---------------------------------------------------------------- confirm
api.onConfirm(({ id, text }) => {
  $('ctext').textContent = text;
  $('confirm').classList.add('show');
  const done = (ok) => { $('confirm').classList.remove('show'); api.confirmReply(id, ok); };
  $('yes').onclick = () => done(true);
  $('no').onclick = () => done(false);
});

// ------------------------------------------------------------------ voice
function speak(text) {
  try {
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(String(text).replace(/[*`#]/g, ''));
    u.lang = navigator.language || 'sv-SE';
    u.onend = () => { if (convo) toggleMic(); };
    speechSynthesis.speak(u);
  } catch {}
}
let rec = null, recording = false;
async function toggleMic() {
  if (recording) { rec && rec.stop(); return; }
  speechSynthesis.cancel();
  let stream;
  try { stream = await navigator.mediaDevices.getUserMedia({ audio: true }); }
  catch { addMsg(false, 'Mikrofonen nekades eller saknas.'); return; }
  const chunks = [];
  rec = new MediaRecorder(stream, { mimeType: 'audio/webm' });
  const ctx = new AudioContext();
  const an = ctx.createAnalyser(); an.fftSize = 1024;
  ctx.createMediaStreamSource(stream).connect(an);
  const buf = new Uint8Array(an.fftSize);
  let heard = false, silentSince = Date.now(), startedAt = Date.now();
  const timer = setInterval(() => {
    an.getByteTimeDomainData(buf);
    let s = 0; for (const v of buf) s += (v - 128) * (v - 128);
    const rms = Math.sqrt(s / buf.length);
    if (rms > 6) { heard = true; silentSince = Date.now(); }
    const silent = Date.now() - silentSince;
    if ((heard && silent > 1300) || (!heard && Date.now() - startedAt > 7000) || Date.now() - startedAt > 30000) rec.stop();
  }, 100);
  rec.ondataavailable = (e) => chunks.push(e.data);
  rec.onstop = async () => {
    clearInterval(timer); ctx.close();
    stream.getTracks().forEach((t) => t.stop());
    recording = false; $('mic').textContent = '🎤'; $('mic').classList.remove('on');
    if (!heard) { if (convo) { convo = false; $('convo').classList.remove('on'); } return; }
    setStatus('Lyssnar…');
    const r = await api.stt(await new Blob(chunks, { type: 'audio/webm' }).arrayBuffer());
    clearStatus();
    if (r.ok && r.text) send(r.text, true); else if (!r.ok) addMsg(false, 'Fel: ' + r.error);
  };
  rec.start(); recording = true; $('mic').textContent = '●'; $('mic').classList.add('on');
}
$('mic').onclick = toggleMic;
$('speak').onclick = () => { speakOn = !speakOn; $('speak').textContent = speakOn ? '🔊' : '🔇'; if (!speakOn) speechSynthesis.cancel(); };
$('convo').onclick = () => {
  convo = !convo; $('convo').classList.toggle('on', convo);
  if (convo) { speakOn = true; $('speak').textContent = '🔊'; toggleMic(); } else speechSynthesis.cancel();
};

// ------------------------------------------------------------------ setup
async function init() {
  const s = await api.getSettings();
  $('setup').style.display = s.hasKey ? 'none' : 'block';
  addMsg(false, 'Hej! Vad vill du göra? Ctrl+Alt+Space öppnar menyn, Ctrl+Alt+A ger ett snabbsvar.');
  refreshChips();
}
$('savekey').onclick = async () => { await api.setSettings({ apiKey: $('key').value }); $('key').value = ''; $('setup').style.display = 'none'; addMsg(false, 'Nyckeln är sparad.'); };
api.onShown(refreshChips);
setInterval(() => { if (!busy) refreshChips(); }, 6000);
init();

// ---- sidebar, menu, views
let preset = null;
const TILES = [
  ['💬', 'Chatt', () => api.setMode('chat')],
  ['✅', 'Svara på frågan', () => api.quick()],
  ['🧩', 'Lös uppgiften', () => go('Lös uppgiften som syns på skärmen och utför den om det går.', true)],
  ['🔎', 'Google', () => go('google ', false)],
  ['✍️', 'Skriv', () => go('skriv ', false)],
  ['💌', 'Svara på meddelande', () => go('Föreslå ett kort svar på meddelandet på skärmen.', true)],
  ['📄', 'Sammanfatta', () => go('Sammanfatta det som syns på skärmen kort.', true)],
  ['🌍', 'Översätt', () => go('Översätt texten på skärmen till svenska.', true)],
  ['🫥', 'Hide-läge', () => api.setMode('hide')],
  ['🎤', 'Röst', () => { preset = { voice: true }; api.setMode('chat'); }],
  ['➖', 'Minimera', () => api.setMode('bar')],
  ['✕', 'Stäng', () => api.quit()],
];
function go(text, auto) { preset = { text, auto }; api.setMode('chat'); }
TILES.forEach(([i, l, f]) => {
  const d = document.createElement('div'); d.className = 'tile';
  d.innerHTML = '<i>' + i + '</i><span>' + l + '</span>'; d.onclick = f; $('grid').appendChild(d);
});
api.onView((v) => {
  document.body.dataset.v = v;
  if (v !== 'chat' || !preset) return;
  const p = preset; preset = null;
  if (p.voice) { setTimeout(() => toggleMic(), 200); return; }
  if (p.auto) send(p.text, false);
  else { $('inp').value = p.text; $('inp').focus(); }
});
{
  let d = false, mv = false, sy = 0;
  $('bar').addEventListener('mousedown', (e) => { d = true; mv = false; sy = e.screenY; });
  window.addEventListener('mousemove', (e) => { if (!d) return; if (Math.abs(e.screenY - sy) > 5) mv = true; if (mv) api.barMove(e.screenY); });
  window.addEventListener('mouseup', () => { if (!d) return; d = false; if (!mv) api.setMode('menu'); });
}
