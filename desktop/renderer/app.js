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
const FEATURE_DEFAULTS = {
  smartReply:true, contextualSuggestions:true, screenActions:true, smartTranslate:true,
  studyMode:true, writingMode:true, webResearch:true, appProfiles:true, selectionAsk:true, voice:true, readAloud:true
};
let features = { ...FEATURE_DEFAULTS };
const FEATURE_META = [
  ['smartReply','💬','Smart Reply','Skriver ett naturligt svar utifrån mejlet eller chatten på skärmen.'],
  ['contextualSuggestions','✨','Smarta förslag','Visar relevanta snabbval beroende på vilken app du använder.'],
  ['screenActions','⚡','AI Actions','Hjälper dig utföra uppgifter i flera steg på skärmen.'],
  ['smartTranslate','🌐','Smart Translate','Översätter text direkt i appar och behåller sammanhanget.'],
  ['studyMode','🎓','Study Mode','Förklarar frågor och uppgifter steg för steg.'],
  ['writingMode','✍️','Writing Mode','Skriver, förbättrar och anpassar texter efter din ton.'],
  ['webResearch','🔎','Web Research','Söker, jämför källor och sammanfattar ämnen.'],
  ['appProfiles','🧩','App Profiles','Anpassar förslag efter aktiv app och arbetsflöde.'],
  ['selectionAsk','🖱️','Markera och fråga','Markera text och använd Ctrl + Alt + C för att fråga om den.'],
  ['voice','🎙','Röstassistent','Aktiverar mikrofon och samtal i realtid.'],
  ['readAloud','🔊','Läs upp svar','Läser upp AI-svar automatiskt när det är aktiverat.'],
];
const MAIL = ['outlook', 'olk', 'thunderbird', 'hxoutlook'];
const CHAT = ['whatsapp', 'telegram', 'discord', 'slack', 'teams', 'ms-teams', 'signal', 'messenger'];
const WEB = ['chrome', 'msedge', 'firefox', 'brave', 'opera', 'vivaldi'];
function chipsFor(title, proc) {
  const t = (title || '').toLowerCase(), p = (proc || '').toLowerCase(), out = [];
  const isMail = MAIL.includes(p) || /gmail|inbox|outlook|mail/.test(t);
  const isChat = CHAT.includes(p) || /whatsapp|messenger|telegram|discord|slack|teams/.test(t);
  const isWeb = WEB.includes(p);
  if (features.contextualSuggestions && isMail) out.push(['Smart Reply', null, true], ['Sammanfatta mejlet', 'Sammanfatta mejlet på skärmen i några punkter och säg vad som förväntas av mig.'], ['Skriv nytt mejl', 'Hjälp mig skriva ett nytt mejl. Fråga vad det ska handla om och till vem.']);
  else if (features.contextualSuggestions && isChat) out.push(['Smart Reply', null, true], ['Sammanfatta chatten', 'Sammanfatta konversationen på skärmen kort.'], ['Översätt', 'Översätt de senaste meddelandena på skärmen till svenska (eller engelska om de redan är på svenska).']);
  else if (features.contextualSuggestions && isWeb) out.push(['Sammanfatta sidan', 'Sammanfatta sidan på skärmen i korta punkter.'], ['Sök mer om detta', 'Sök på webben efter mer information om ämnet på skärmen och sammanfatta vad du hittar.'], ['Förklara', 'Förklara enkelt det som står på skärmen.']);
  out.push(['Svara på frågan', 'Svara på frågan som syns på skärmen. Ge svaret tydligt.'], ['Sök på Google', 'Sök på webben efter svaret på frågan på skärmen och förklara det kort.'],
    ['Förklara skärmen', 'Förklara kort vad som visas på skärmen.'], ['Sammanfatta', 'Sammanfatta texten på skärmen kort.'],
    ['Översätt', 'Översätt texten på skärmen till svenska (eller engelska om den redan är svenska).'], ['Skriv text', 'Hjälp mig skriva en text utifrån det som visas på skärmen. Fråga om något saknas.'],
    ['Fixa', 'Hitta fel i texten på skärmen (stavning, fakta, logik) och föreslå rättelser.']);
  if (!features.contextualSuggestions) return [];
  const gated = out.filter(([label]) => {
    if (/Översätt/i.test(label)) return features.smartTranslate;
    if (/Sök|Google/i.test(label)) return features.webResearch;
    if (/Skriv|Fixa/i.test(label)) return features.writingMode;
    return true;
  });
  const seen = new Set();
  return gated.filter((x) => (seen.has(x[0]) ? false : seen.add(x[0]))).slice(0, 7);
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
  const w = chat.querySelector('.welcome'); if (w) w.remove();
  addMsg(true, text);
  live = null; liveText = '';
  showDots();
  try {
    const r = await api.chat(text, voice);
    clearStatus(); clearDots();
    if (live && r.ok) { live.innerHTML = md(r.reply); live = null; } else { if (live) { live.remove(); live = null; } addMsg(false, r.ok ? r.reply : 'Fel: ' + r.error); }
    if (r.ok && features.readAloud && (speakOn || voice)) speak(r.reply);
  } catch (e) { clearStatus(); clearDots(); addMsg(false, 'Fel: ' + e.message); }
  busy = false;
}
api.onStatus(setStatus);
$('go').onclick = () => { const t = inp.value; inp.value = ''; send(t, false); };
inp.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('go').click(); }
});
$('new').onclick = async () => { await api.newChat(); chat.textContent = ''; $('draftcard').classList.remove('show'); showWelcome(); refreshChips(); };
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
  ['Vänligare', 'Skriv om i en varmare, vänligare ton.'], ['Kort svar', 'Gör svaret kort och naturligt, som ett vanligt snabbt meddelande.'],
  ['Mer avslappnat', 'Skriv mer avslappnat och mänskligt utan att bli slarvig.'], ['Mer professionellt', 'Skriv mer professionellt och tydligt, men behåll en naturlig ton.'],
  ['Översätt', 'Översätt till engelska. Om texten redan är engelska: översätt till svenska.'],
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
$('copy').onclick = async () => { api.styleAdd($('draft').value); await api.copy($('draft').value); setStatus('Kopierat.'); setTimeout(clearStatus, 1500); };
async function insert(sendIt) {
  const t = $('draft').value; if (!t.trim()) return;
  api.styleAdd(t);
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
  showWelcome();
  refreshChips();
}
$('savekey').onclick = async () => { await api.setSettings({ apiKey: $('key').value }); $('key').value = ''; $('setup').style.display = 'none'; addMsg(false, 'Nyckeln är sparad.'); };
api.onShown(refreshChips);
setInterval(() => { if (!busy) refreshChips(); }, 6000);
init();

// ---- sidebar, menu, views
let preset = null;
function go(text, auto) { preset = { text, auto }; api.setMode('chat'); }
function showWelcome() {
  const d = document.createElement('div'); d.className = 'welcome';
  d.innerHTML = '<div class="big">👋</div><h3>Hej! Vad vill du göra?</h3><p>Jag ser din skärm och kan hjälpa dig direkt.</p><div class="sug"></div><small>Ctrl+Alt+Space meny · Ctrl+Alt+A snabbsvar · Ctrl+Alt+C förklara markering</small>';
  for (const [l, t] of [['✅ Svara på frågan på skärmen', 'Svara på frågan som syns på skärmen. Ge svaret tydligt.'], ['📄 Sammanfatta det jag tittar på', 'Sammanfatta det som syns på skärmen kort.'], ['🔎 Googla något', null]]) {
    const b = document.createElement('button'); b.textContent = l;
    b.onclick = () => (t ? send(t, false) : (inp.value = 'google ', inp.focus()));
    d.querySelector('.sug').appendChild(b);
  }
  chat.appendChild(d);
}
const BUILTIN = [
  ['DIREKT HJÄLP', [
    ['💬', 'Öppna chatten', () => api.setMode('chat')],
    ['✨', 'Smart Reply', () => { preset = { reply:true }; api.setMode('chat'); }, 'smartReply'],
    ['✅', 'Svara på skärmen', () => api.quick()],
    ['⚡', 'AI Actions', () => go('Hjälp mig genomföra uppgiften på skärmen steg för steg. Planera först och be om bekräftelse innan du skickar, raderar, köper eller publicerar.', true), 'screenActions'],
  ]],
  ['AI-VERKTYG', [
    ['🌐', 'Smart Translate', () => go('Översätt den viktigaste texten på skärmen till svenska. Behåll struktur, ton och sammanhang.', true), 'smartTranslate'],
    ['🎓', 'Study Mode', () => go('Förklara uppgiften pedagogiskt steg för steg. Ge ledtrådar innan du visar hela lösningen och kontrollera min förståelse.', true), 'studyMode'],
    ['✍️', 'Writing Mode', () => go('Hjälp mig skriva eller förbättra texten på skärmen. Matcha sammanhang, språk och min skrivstil.', true), 'writingMode'],
    ['🔎', 'Web Research', () => go('Undersök ämnet på skärmen, jämför tillförlitliga källor och sammanfatta med länkar.', true), 'webResearch'],
    ['🧩', 'App Profiles', () => go('Identifiera appen jag använder och anpassa automatiskt hjälpen efter dess arbetsflöde.', true), 'appProfiles'],
  ]],
  ['VERKTYG', [
    ['🎙', 'Röstassistent', () => { preset = { voice:true }; api.setMode('chat'); }, 'voice'],
    ['🕘', 'Historik', () => { preset = { hist:true }; api.setMode('chat'); }],
    ['🧠', 'Minne', () => go('visa minne', true)],
    ['🫥', 'Hide-läge', () => api.setMode('hide')],
  ]],
];
let upd = { state: 'idle' }, autostartOn = false, memoryAutoOn = false, cmds = [];
function tileEl(icon, label, fn, extra) {
  const d = document.createElement('div'); d.className = 'tile'; d.tabIndex = 0; d.setAttribute('role', 'button');
  d.innerHTML = '<i>' + icon + '</i><span></span>'; d.querySelector('span').textContent = label;
  d.onclick = () => fn(d);
  d.onkeydown = (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fn(d); } };
  if (extra) d.appendChild(extra);
  return d;
}
function group(title, tiles) {
  const wrap = document.createElement('div');
  const t = document.createElement('div'); t.className = 'gt'; t.textContent = title;
  const g = document.createElement('div'); g.className = 'grid';
  tiles.forEach((x) => g.appendChild(x));
  wrap.append(t, g); return wrap;
}
function renderMenu() {
  const box = $('groups'); box.textContent = '';
  const q = ($('featureSearch')?.value || '').trim().toLowerCase();
  for (const [title, items] of BUILTIN) {
    const visible = items.filter((x) => (!x[3] || features[x[3]]) && (!q || x[1].toLowerCase().includes(q)));
    if (visible.length) box.appendChild(group(title, visible.map(([i, l, fn]) => tileEl(i, l, fn))));
  }
  const mine = cmds.filter((c) => !q || c.name.toLowerCase().includes(q)).map((c) => {
    const te = document.createElement('div'); te.className = 'te';
    const ed = document.createElement('b'); ed.textContent = '✎'; ed.title = 'Redigera';
    ed.onclick = (e) => { e.stopPropagation(); openForm(c); };
    const rm = document.createElement('b'); rm.textContent = '×'; rm.title = 'Ta bort';
    rm.onclick = async (e) => { e.stopPropagation(); cmds = await api.commandsDelete(c.id); renderMenu(); };
    te.append(ed, rm);
    return tileEl(c.emoji || '⚡', c.name, () => go(c.screen ? c.prompt : c.prompt + '\n\n(Du behöver inte titta på skärmen.)', true), te);
  });
  const add = tileEl('＋', 'Nytt kommando', () => openForm(null)); add.classList.add('add');
  if (!q || 'egna kommandon nytt kommando'.includes(q)) box.appendChild(group('EGNA KOMMANDON', [...mine, add]));
  const sys = [
    tileEl('⚙️', 'Inställningar', () => api.openSettingsWindow()),
    tileEl('🚀', 'Autostart: ' + (autostartOn ? 'på' : 'av'), async () => { autostartOn = await api.autostartSet(!autostartOn); renderMenu(); }),
    tileEl(upd.state === 'ready' ? '🔄' : '⬇️', upd.state === 'ready' ? 'Starta om och uppdatera' : upd.state === 'downloading' ? 'Hämtar…' : upd.state === 'checking' ? 'Söker…' : upd.state === 'latest' ? 'Senaste versionen' : 'Sök uppdatering', () => (upd.state === 'ready' ? api.updateInstall() : api.updateCheck())),
    tileEl('🧠', 'Minneslärande: ' + (memoryAutoOn ? 'på' : 'av'), async () => { memoryAutoOn = await api.memoryAutoSet(!memoryAutoOn); renderMenu(); }),
    tileEl('➖', 'Minimera', () => api.setMode('bar')),
  ];
  if (!q || 'system inställningar autostart uppdatering minne minimera'.includes(q)) box.appendChild(group('SYSTEM', sys));
}
function renderFeatureToggles() {
  const box = $('featureToggles'); box.textContent = '';
  for (const [key, icon, label, detail] of FEATURE_META) {
    const row = document.createElement('label'); row.className = 'feature-row';
    const ico = document.createElement('span'); ico.className = 'feature-icon'; ico.textContent = icon;
    const copy = document.createElement('span'); copy.className = 'feature-copy';
    const title = document.createElement('b'); title.textContent = label;
    const desc = document.createElement('small'); desc.textContent = detail;
    copy.append(title, desc);
    const toggle = document.createElement('input'); toggle.type = 'checkbox'; toggle.checked = !!features[key]; toggle.setAttribute('aria-label', label);
    toggle.onchange = async () => { features[key] = toggle.checked; features = await api.featuresSet(features); applyFeatureVisibility(); renderMenu(); refreshChips(); };
    row.append(ico, copy, toggle); box.appendChild(row);
  }
}
function applyFeatureVisibility() {
  for (const id of ['mic','speak','convo']) $(id).hidden = !features.voice;
}
function openSettings() {
  $('settingsPanel').classList.add('show'); renderFeatureToggles();
}
function closeSettings() { $('settingsPanel').classList.remove('show'); }
$('settingsOpen').onclick = () => api.openSettingsWindow();
$('settingsBack').onclick = closeSettings;
$('settingsDone').onclick = closeSettings;
$('featureSearch').addEventListener('input', renderMenu);
let editId = null;
function openForm(c) {
  editId = c ? c.id : null;
  $('ftitle').textContent = c ? 'Redigera snabbkommando' : 'Nytt snabbkommando';
  $('f-emoji').value = c ? c.emoji : ''; $('f-name').value = c ? c.name : ''; $('f-prompt').value = c ? c.prompt : ''; $('f-screen').checked = c ? c.screen : true;
  $('cform').classList.add('show'); $('f-name').focus();
}
$('f-cancel').onclick = () => $('cform').classList.remove('show');
$('f-save').onclick = async () => {
  if (!$('f-name').value.trim() || !$('f-prompt').value.trim()) { (!$('f-name').value.trim() ? $('f-name') : $('f-prompt')).focus(); return; }
  cmds = await api.commandsSave({ id: editId || undefined, emoji: $('f-emoji').value, name: $('f-name').value, prompt: $('f-prompt').value, screen: $('f-screen').checked });
  $('cform').classList.remove('show'); renderMenu();
};
// update banners
function renderUpdate() {
  for (const id of ['upd-m', 'upd-c']) {
    const el = $(id); el.textContent = '';
    if (upd.state === 'ready') {
      const s = document.createElement('span'); s.textContent = 'Ny version hämtad' + (upd.version ? ' (' + upd.version + ')' : '') + '.';
      const b = document.createElement('button'); b.textContent = 'Starta om och uppdatera'; b.onclick = () => api.updateInstall();
      el.append(s, b); el.classList.add('show');
    } else if (upd.state === 'downloading') { el.textContent = 'Hämtar ny version i bakgrunden…'; el.classList.add('show'); }
    else el.classList.remove('show');
  }
  renderMenu();
}
api.onUpdate((s) => { upd = s; renderUpdate(); });
Promise.all([api.commandsGet(), api.autostartGet(), api.updateState(), api.memoryAutoGet(), api.featuresGet()]).then(([c, a, u, mm, ff]) => {
  cmds = c; autostartOn = a; upd = u; memoryAutoOn = mm; features = { ...FEATURE_DEFAULTS, ...(ff || {}) };
  $('ver').textContent = 'v' + (u.version || ''); applyFeatureVisibility(); renderUpdate();
});
// history
let hqTimer = null;
function fmtDate(ts) {
  const d = new Date(ts), n = new Date();
  const t = d.toLocaleTimeString('sv-SE', { hour: '2-digit', minute: '2-digit' });
  if (d.toDateString() === n.toDateString()) return 'Idag ' + t;
  const y = new Date(n - 864e5); if (d.toDateString() === y.toDateString()) return 'Igår ' + t;
  return d.toLocaleDateString('sv-SE', { day: 'numeric', month: 'short' }) + ' ' + t;
}
async function renderHist() {
  const q = $('hq').value.trim(), list = await api.archiveList(q), box = $('hlist');
  box.textContent = '';
  if (!list.length) { const e = document.createElement('div'); e.className = 'empty'; e.textContent = q ? 'Inga träffar.' : 'Inga sparade konversationer än.'; box.appendChild(e); return; }
  for (const c of list) {
    const it = document.createElement('div'); it.className = 'it'; it.tabIndex = 0;
    const tx = document.createElement('div'); tx.className = 'tx';
    tx.innerHTML = '<div class="tt">' + Lib.highlightHtml(c.title, q) + '</div>' + (c.snippet ? '<div class="sn">' + Lib.highlightHtml(c.snippet, q) + '</div>' : '') + '<div class="mt">' + fmtDate(c.ts) + ' · ' + c.count + ' meddelanden</div>';
    const del = document.createElement('button'); del.className = 'del'; del.textContent = '🗑'; del.title = 'Ta bort';
    del.onclick = async (e) => { e.stopPropagation(); await api.archiveDelete(c.id); renderHist(); };
    const open = async () => {
      const msgs = await api.archiveOpen(c.id); if (!msgs) return;
      chat.textContent = ''; $('draftcard').classList.remove('show');
      for (const m of msgs) addMsg(m.role === 'user', m.content);
      closeHist();
    };
    it.onclick = open; it.onkeydown = (e) => { if (e.key === 'Enter') open(); };
    it.append(tx, del); box.appendChild(it);
  }
}
function openHist() { $('hist').classList.add('show'); $('hq').value = ''; renderHist(); $('hq').focus(); }
function closeHist() { $('hist').classList.remove('show'); inp.focus(); }
$('histbtn').onclick = openHist;
$('hback').onclick = closeHist;
$('hq').oninput = () => { clearTimeout(hqTimer); hqTimer = setTimeout(renderHist, 120); };
{ let sure = false; $('hclear').onclick = async () => { if (!sure) { sure = true; $('hclear').textContent = 'Säker?'; setTimeout(() => { sure = false; $('hclear').textContent = 'Rensa'; }, 3000); return; } sure = false; $('hclear').textContent = 'Rensa'; await api.archiveClear(); renderHist(); }; }
window.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') { if ($('settingsPanel').classList.contains('show')) closeSettings(); else if ($('cform').classList.contains('show')) $('cform').classList.remove('show'); else if ($('hist').classList.contains('show')) closeHist(); else api.setMode('bar'); }
  else if (e.ctrlKey && e.key.toLowerCase() === 'h' && document.body.dataset.v === 'chat') { e.preventDefault(); openHist(); }
  else if (e.ctrlKey && e.key.toLowerCase() === 'k') { e.preventDefault(); if ($('hist').classList.contains('show')) $('hq').focus(); else inp.focus(); }
});
api.onView((v) => {
  document.body.dataset.v = v;
  if (v !== 'chat' || !preset) return;
  const p = preset; preset = null;
  if (p.voice) { if (features.voice) setTimeout(() => toggleMic(), 200); return; }
  if (p.reply) { if (features.smartReply) setTimeout(makeReply, 160); return; }
  if (p.hist) { openHist(); return; }
  if (p.auto) send(p.text, false);
  else { $('inp').value = p.text; $('inp').focus(); }
});
{
  let d = false, mv = false, sy = 0;
  $('bar').addEventListener('mousedown', (e) => { d = true; mv = false; sy = e.screenY; });
  window.addEventListener('mousemove', (e) => { if (!d) return; if (Math.abs(e.screenY - sy) > 5) mv = true; if (mv) api.barMove(e.screenY); });
  window.addEventListener('mouseup', () => { if (!d) return; d = false; if (!mv) api.setMode('menu'); });
}

api.onAsk((t) => send(t, false));

