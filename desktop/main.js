const { app, Tray, Menu, nativeImage, BrowserWindow, globalShortcut, screen, desktopCapturer, ipcMain, clipboard, shell, session } = require('electron');
const path = require('path');
const fs = require('fs');
const { execFile, spawn } = require('child_process');

const MODEL = 'gpt-4o';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

if (!app.requestSingleInstanceLock()) app.quit();

// ------------------------------------------------------------------ storage
function file(name) { return path.join(app.getPath('userData'), name); }
function readJson(name, def) { try { return JSON.parse(fs.readFileSync(file(name), 'utf8')); } catch { return def; } }
function writeJson(name, v) { try { fs.mkdirSync(app.getPath('userData'), { recursive: true }); fs.writeFileSync(file(name), JSON.stringify(v)); } catch {} }
function embeddedKey() {
  try {
    const c = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8'));
    return typeof c.openaiKey === 'string' && c.openaiKey.startsWith('sk-') ? c.openaiKey : '';
  } catch { return ''; }
}
function apiKey() { return embeddedKey() || readJson('settings.json', {}).apiKey || ''; }
const Lib = require('./renderer/lib.js');
let curConv = null;
function history() { return readJson('history.json', []); }
function addHistory(role, content) {
  const h = history(); h.push({ role, content: String(content).slice(0, 2000) });
  writeJson('history.json', h.slice(-24));
  const r = Lib.archiveAppend(readJson('archive.json', []), curConv, role, content, Date.now());
  curConv = r.id; writeJson('archive.json', r.arr);
}
function memory() { return readJson('memory.json', []); }
function addMemory(f, force) { writeJson('memory.json', Lib.mergeMemory(memory(), [f], 40, force !== false)); }
function styleSamples() { return readJson('style.json', []); }
function styleBlock() {
  const s = styleSamples().slice(-3);
  return s.length ? '\nSå brukar användaren skriva (stilprov, härma tonen):\n' + s.map((x) => '- ' + x.replace(/\n/g, ' ')).join('\n') : '';
}
// Quietly learns lasting facts (people, tone, habits) from an exchange. Never throws.
async function learn(userText, answer) {
  try {
    if (readJson('settings.json', {}).memoryAuto !== true || !apiKey() || String(userText).length < 12 || Lib.isSensitive(userText)) return;
    const j = await post('https://api.openai.com/v1/chat/completions', {
      model: 'gpt-4o-mini', max_tokens: 300, temperature: 0, response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: 'Du plockar ut VARAKTIGA fakta om användaren ur ett utbyte med en assistent: personer användaren skriver till (namn och relation), önskad ton eller språk per person, vanor och preferenser. Skriv varje fakta som en kort svensk mening. Ta ALDRIG med lösenord, kortnummer, personnummer, hälsa, ekonomi eller annat känsligt, och inget tillfälligt. Svara med JSON {"facts":["..."]}. Inget nytt: {"facts":[]}.' },
        { role: 'user', content: 'Användare: ' + String(userText).slice(0, 800) + '\nAssistent: ' + String(answer).slice(0, 600) },
      ],
    });
    const facts = JSON.parse(j.choices[0].message.content).facts;
    if (Array.isArray(facts) && facts.length) writeJson('memory.json', Lib.mergeMemory(memory(), facts.slice(0, 4), 40, false));
  } catch {}
}
function commands() { return readJson('commands.json', []).map(Lib.sanitizeCommand).filter(Boolean); }
const FEATURE_DEFAULTS = {
  smartReply: true, activeHelp: true, contextualSuggestions: true, screenActions: true,
  smartTranslate: true, studyMode: true, writingMode: true,
  webResearch: true, appProfiles: true, selectionAsk: true, voice: true, readAloud: true,
};
function featureSettings() { return { ...FEATURE_DEFAULTS, ...(readJson('settings.json', {}).features || {}) }; }
function assistantAppearance() {
  const saved = readJson('settings.json', {});
  return {
    display: ['sidebar', 'bubble', 'swipe'].includes(saved.assistantDisplay) ? saved.assistantDisplay : 'sidebar',
    swipe: saved.assistantSwipe === 'right' ? 'right' : 'left',
  };
}

const SMART_REPLY_APPS = {
  outlook: 'Outlook',
  olk: 'Outlook (new)',
  thunderbird: 'Thunderbird',
  whatsapp: 'WhatsApp',
  telegram: 'Telegram',
  discord: 'Discord',
  slack: 'Slack',
  teams: 'Microsoft Teams',
  'ms-teams': 'Microsoft Teams (new)',
  signal: 'Signal',
  browser: 'Webbläsare (Gmail, Outlook, WhatsApp Web m.fl.)',
};
function smartReplyApps() {
  const saved = readJson('settings.json', {}).smartReplyApps;
  return Array.isArray(saved) ? saved.filter((x) => Object.prototype.hasOwnProperty.call(SMART_REPLY_APPS, x)) : Object.keys(SMART_REPLY_APPS);
}
function smartReplyAllowed(proc, title) {
  const p = String(proc || '').toLowerCase(), t = String(title || '').toLowerCase(), enabled = smartReplyApps();
  const browsers = ['chrome', 'msedge', 'firefox', 'brave', 'opera', 'vivaldi'];
  if (browsers.includes(p)) return enabled.includes('browser') && /(gmail|outlook|mail|whatsapp|messenger|telegram|discord|slack|teams|signal)/.test(t);
  return enabled.includes(p);
}

// ------------------------------------------------------------------- OpenAI
async function post(url, body) {
  const r = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + apiKey() },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(75000),
  });
  const t = await r.text();
  if (!r.ok) {
    let m = t.slice(0, 200);
    try { m = JSON.parse(t).error.message; } catch {}
    throw new Error('OpenAI ' + r.status + ': ' + m);
  }
  return JSON.parse(t);
}
function content(text, shot) {
  const c = [{ type: 'text', text }];
  if (shot) c.push({ type: 'image_url', image_url: { url: 'data:image/jpeg;base64,' + shot, detail: 'high' } });
  return c;
}
async function chat(messages, o = {}) {
  const body = { model: MODEL, messages, max_tokens: o.maxTokens || 900 };
  if (o.tools) { body.tools = o.tools; body.tool_choice = 'auto'; }
  if (o.json) body.response_format = { type: 'json_object' };
  if (o.temperature !== undefined) body.temperature = o.temperature;
  const j = await post('https://api.openai.com/v1/chat/completions', body);
  return j.choices[0].message;
}
// Streaming variant: forwards text deltas while the model talks, accumulates tool calls.
async function chatStream(messages, o, onDelta, onTool) {
  const body = { model: MODEL, messages, max_tokens: o.maxTokens || 900, stream: true };
  if (o.tools) { body.tools = o.tools; body.tool_choice = 'auto'; }
  const r = await fetch('https://api.openai.com/v1/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + apiKey() },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(90000),
  });
  if (!r.ok) {
    const t = await r.text();
    let m = t.slice(0, 200);
    try { m = JSON.parse(t).error.message; } catch {}
    throw new Error('OpenAI ' + r.status + ': ' + m);
  }
  const dec = new TextDecoder();
  let buf = '', text = '', toolsSeen = false;
  const calls = [];
  for await (const chunk of r.body) {
    buf += dec.decode(chunk, { stream: true });
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (!line.startsWith('data:')) continue;
      const data = line.slice(5).trim();
      if (data === '[DONE]') continue;
      let j; try { j = JSON.parse(data); } catch { continue; }
      const d = j.choices && j.choices[0] && j.choices[0].delta;
      if (!d) continue;
      if (d.content) { text += d.content; if (!toolsSeen) onDelta(d.content); }
      for (const tc of d.tool_calls || []) {
        if (!toolsSeen) { toolsSeen = true; onTool(); }
        const c = calls[tc.index] || (calls[tc.index] = { id: '', type: 'function', function: { name: '', arguments: '' } });
        if (tc.id) c.id = tc.id;
        if (tc.function && tc.function.name) c.function.name += tc.function.name;
        if (tc.function && tc.function.arguments) c.function.arguments += tc.function.arguments;
      }
    }
  }
  const msg = { role: 'assistant', content: text };
  const real = calls.filter(Boolean);
  if (real.length) msg.tool_calls = real;
  return msg;
}
async function textCompletion(system, user, maxTokens = 700) {
  const m = await chat([{ role: 'system', content: system }, { role: 'user', content: user }], { maxTokens });
  return (m.content || '').trim();
}
async function webSearch(query) {
  const j = await post('https://api.openai.com/v1/chat/completions', {
    model: 'gpt-4o-search-preview',
    web_search_options: {},
    messages: [
      { role: 'system', content: 'Sök på webben och svara kort och korrekt på användarens språk. Ge konkreta fakta och jämför källor när det behövs.' },
      { role: 'user', content: query },
    ],
  });
  const m = j.choices[0].message;
  const src = new Map();
  for (const a of m.annotations || []) {
    const u = a.url_citation;
    if (u && u.url && !src.has(u.url)) src.set(u.url, u.title || u.url);
  }
  const list = [...src.entries()].slice(0, 4).map(([u, t]) => '- ' + t + ' (' + u + ')').join('\n');
  return (m.content || '').trim() + (list ? '\n\nKällor:\n' + list : '');
}

const QUICK_SYSTEM =
  'Du löser frågor, quiz och uppgifter som syns på en datorskärm. Du får en skärmbild. ' +
  'Gör så här: 1) Hitta alla frågor och deras svarsalternativ. 2) Gäller frågan en bild, eller är alternativen bilder: titta noga på bilden/bilderna och jämför dem med frågan innan du väljer. ' +
  '3) Tänk igenom varje fråga noggrant och räkna matte steg för steg i fältet "reasoning". 4) Välj det alternativ som är rätt.\n' +
  'Svara ENDAST med JSON: {"reasoning":"kort tankegång","answers":[{"q":"frågans nummer, t.ex. 1","option":"alternativets bokstav eller siffra, t.ex. B eller 3","answer":"svaret i klartext, kort, t.ex. Paris","confidence":0.0-1.0}]}\n' +
  'Regler: Ett objekt per fråga, i den ordning de står. "option" ska ALLTID fyllas i när frågan har alternativ. ' +
  'Saknar alternativen bokstav/siffra: räkna dem uppifrån och ned (eller vänster till höger) som 1, 2, 3 och skriv numret. ' +
  'Vid bildalternativ: beskriv den rätta bilden med några ord i "answer". Flera rätta alternativ: skriv alla i "option", t.ex. "A, C". ' +
  'Fråga utan alternativ: lämna "option" tom och skriv svaret i "answer". Om ingen fråga syns: answers är en tom lista. Svara på samma språk som frågorna.';

function formatAnswers(raw) {
  try {
    const arr = JSON.parse(raw).answers;
    if (!Array.isArray(arr) || !arr.length) return '–';
    return arr.map((o) => {
      const q = String(o.q || '').trim(), opt = String(o.option || '').trim(), ans = String(o.answer || '').trim();
      const core = opt && ans && opt.toLowerCase() !== ans.toLowerCase() ? opt + ' – ' + ans : opt || ans;
      return q ? q + ': ' + core : core;
    }).join('\n');
  } catch { return String(raw).slice(0, 200); }
}
async function askOnce(shot) {
  const messages = [
    { role: 'system', content: QUICK_SYSTEM },
    { role: 'user', content: content('Lös frågorna som syns på skärmen. Svara med JSON enligt instruktionen.', shot) },
  ];
  let m = await chat(messages, { json: true, temperature: 0, maxTokens: 900 });
  let raw = m.content || '';
  let unsure = false;
  try { unsure = (JSON.parse(raw).answers || []).some((o) => Number(o.confidence === undefined ? 1 : o.confidence) < 0.85); } catch {}
  if (unsure) {
    // Second, critical pass only when the first answer was uncertain.
    messages.push({ role: 'assistant', content: raw },
      { role: 'user', content: 'Granska ditt svar kritiskt fråga för fråga mot bilden. Räkna om och rätta det som är fel. Svara med samma JSON-format.' });
    try { m = await chat(messages, { json: true, temperature: 0, maxTokens: 1100 }); raw = m.content || raw; } catch {}
  }
  return formatAnswers(raw);
}

// --------------------------------------------------------------- PowerShell
function psOnce(script, timeout) {
  const enc = Buffer.from(script, 'utf16le').toString('base64');
  return new Promise((resolve) => {
    execFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', enc],
      { timeout, windowsHide: true }, (err, stdout, stderr) =>
        resolve({ ok: !err, out: (stdout || '').trim(), err: (stderr || '').trim() }));
  });
}
// One long-lived PowerShell process: no startup cost per action, so mouse, typing and polling feel instant.
let pss = null, pssQueue = Promise.resolve(), pssSeq = 0;
function pssStart() {
  const p = spawn('powershell.exe', ['-NoProfile', '-NoLogo', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', '-'], { windowsHide: true });
  const st = { p, buf: '', done: null, dead: false };
  p.stdout.setEncoding('utf8');
  p.stdout.on('data', (d) => { st.buf += d; if (st.done) st.done(); });
  p.stderr.on('data', () => {});
  p.on('error', () => { st.dead = true; if (st.done) st.done(); });
  p.on('exit', () => { st.dead = true; if (st.done) st.done(); });
  return st;
}
function pssRun(script, timeout) {
  return new Promise((resolve) => {
    if (!pss || pss.dead) pss = pssStart();
    const st = pss, id = ++pssSeq, end = '@@END' + id + '@@';
    st.buf = '';
    const b64 = Buffer.from(script, 'utf8').toString('base64');
    let finished = false;
    const finish = (r) => { if (finished) return; finished = true; clearTimeout(tm); st.done = null; resolve(r); };
    const tm = setTimeout(() => { try { st.p.kill(); } catch {} st.dead = true; finish({ ok: false, out: '', err: 'timeout' }); }, timeout);
    st.done = () => {
      if (st.dead && !st.buf.includes(end)) return finish({ ok: false, out: '', err: 'PowerShell stängdes', dead: true });
      const i = st.buf.indexOf(end);
      if (i < 0) return;
      const out = st.buf.slice(0, i).replace(/\r/g, '').trim();
      const m = out.match(/(?:^|\n)@@ERR@@(.*)$/s);
      finish(m ? { ok: false, out: out.slice(0, m.index), err: m[1].trim() } : { ok: true, out, err: '' });
    };
    try {
      st.p.stdin.write("try { iex ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + b64 + "'))) } catch { Write-Output ('@@ERR@@' + $_.Exception.Message) }; Write-Output '" + end + "'\n");
    } catch { st.dead = true; finish({ ok: false, out: '', err: 'stdin', dead: true }); }
  });
}
let pssReady = null;
function pssInit() {
  if (!pssReady) pssReady = pssRun('[Console]::OutputEncoding=[Text.Encoding]::UTF8\nAdd-Type -AssemblyName System.Windows.Forms\n' + WIN32_SRC, 20000);
  return pssReady;
}
function ps(lines, timeout = 10000) {
  let script = Array.isArray(lines) ? lines.join('\n') : lines;
  script = script.split(WIN32_SRC).join('');
  const run = pssQueue.then(async () => {
    const init = await pssInit();
    if (!init.ok) { pssReady = null; return psOnce(WIN32_SRC + '\n' + script, timeout); }
    let r = await pssRun(script, timeout);
    if (r.dead) { pssReady = null; await pssInit(); r = await pssRun(script, timeout); }
    return r;
  });
  pssQueue = run.catch(() => {});
  return run;
}
const q1 = (s) => String(s).replace(/'/g, "''");
const WIN32 = [
  'Add-Type @"',
  'using System; using System.Runtime.InteropServices; using System.Text;',
  'public class W32 {',
  '  [StructLayout(LayoutKind.Sequential)] public struct PT { public int X; public int Y; }',
  '  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);',
  '  [DllImport("user32.dll")] public static extern bool GetCursorPos(out PT p);',
  '  [DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e);',
  '  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();',
  '  [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);',
  '  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);',
  '}',
  '"@',
];
const WIN32_SRC = WIN32.join('\n');
let lastExternal = { title: '', proc: '' };
let lastActiveHelpSignature = '', lastActiveHelpAt = 0, activeHelpRepeat = 0, activeHelpPrompt = null;
function inspectForActiveHelp(title, proc, snapshot) {
  if (!featureSettings().activeHelp || mode === 'chat' || !panel || panel.isFocused()) { activeHelpRepeat = 0; return; }
  const text = (String(title || '') + '\n' + String(snapshot || '')).toLowerCase();
  if (!text.trim()) return;
  const download = /(download|downloading|nedladd|hämtar|hämtning)/i.test(text);
  const strong = [
    'download failed', "couldn't download", 'could not download', 'failed to download',
    'nedladdningen misslyckades', 'kunde inte ladda ner', 'hämtningen misslyckades',
    'something went wrong', 'not responding', "isn't responding", 'åtkomst nekad',
    'access denied', 'permission denied', 'kunde inte slutföra', "couldn't complete",
    'could not complete', 'network error', 'nätverksfel', 'connection failed',
    'anslutningen misslyckades', 'installation failed', 'installationen misslyckades',
    'upload failed', "couldn't upload", 'could not upload', 'kunde inte ladda upp', 'uppladdningen misslyckades',
    'file type not supported', 'unsupported file type', 'filtypen stöds inte', 'ogiltig e-postadress',
    'invalid email address', 'required field', 'field is required', 'obligatoriskt fält', 'fältet är obligatoriskt',
    'server error', 'form submission failed', 'kunde inte skicka formuläret'
  ].some((phrase) => text.includes(phrase));
  const stalled = download && /(0\s*(b\/s|kb\/s|mb\/s)|0%|paused|pausad|stalled|väntar)/i.test(text);
  const soft = /(try again|retry|försök igen|couldn't|kunde inte|failed|misslyckades|error|fel)/i.test(text);
  if (!strong && !stalled && !soft) { activeHelpRepeat = 0; return; }
  const signature = String(proc || '') + '|' + String(title || '') + '|' + text.replace(/\s+/g, ' ').slice(0, 1300);
  if (signature === lastActiveHelpSignature) activeHelpRepeat++; else { lastActiveHelpSignature = signature; activeHelpRepeat = 1; }
  if (!strong && activeHelpRepeat < (stalled ? 8 : 3)) return;
  if (signature === lastActiveHelpPromptSignature || Date.now() - lastActiveHelpAt < 120000) return;
  lastActiveHelpPromptSignature = signature;
  lastActiveHelpAt = Date.now();
  const headline = download ? 'Nedladdningen verkar ha fastnat' : 'Det verkar ha uppstått ett problem';
  activeHelpPrompt = 'Active Help upptäckte ett möjligt problem i ' + (title || proc) + '. Undersök den aktuella skärmen och förklara kort vad som verkar ha hänt och hur det kan lösas. Gör inga ändringar, klicka inte och ladda inte ner något ännu. Fråga först om jag vill att du tar över och försöker lösa problemet. Förklara vilken åtgärd du tänker utföra innan du ber om godkännande.';
  showAnswer('Active Help\n' + headline + '\nTryck här för att analysera', 12000);
}
let lastActiveHelpPromptSignature = '';
let autoDraftTimer = null, lastObservedDraft = '', lastAutoDraftSuggestion = '', autoDraftBusy = false, autoDraftSuppressedUntil = 0;
function queueSmartDraft(draft, proc, title) {
  const value = String(draft || '').trim();
  const signature = String(proc || '') + '|' + value;
  if (Date.now() < autoDraftSuppressedUntil) { lastObservedDraft = signature; return; }
  if (mode !== 'chat' || !featureSettings().smartReply || !smartReplyAllowed(proc, title)) {
    clearTimeout(autoDraftTimer); autoDraftTimer = null; lastObservedDraft = ''; return;
  }
  if (value.length < 5) {
    clearTimeout(autoDraftTimer); autoDraftTimer = null; lastObservedDraft = signature; return;
  }
  if (signature === lastObservedDraft) return;
  lastObservedDraft = signature;
  clearTimeout(autoDraftTimer);
  autoDraftTimer = setTimeout(() => createAutoDraftSuggestion(value, signature, proc, title), 1350);
}
async function createAutoDraftSuggestion(draft, signature, proc, title) {
  if (autoDraftBusy) { autoDraftTimer = setTimeout(() => createAutoDraftSuggestion(draft, signature, proc, title), 700); return; }
  if (mode !== 'chat' || !panel || !panel.isVisible() || !smartReplyAllowed(proc, title) || lastObservedDraft !== signature) return;
  autoDraftBusy = true;
  try {
    if (panel && !panel.isDestroyed()) panel.webContents.send('smart-draft-loading', true);
    const shot = await captureScreen(true);
    const result = await chat([
      { role: 'system', content: 'Du är ScreenBubbles Smart Reply. Använd det aktiva skrivfältets text som användarens pågående utkast. Förbättra och färdigställ det på ett naturligt sätt, men ändra inte avsikten, hitta inte på fakta eller lägg till löften. Avgör om det är mejl, chatt eller formell kommunikation och anpassa språk, ton och längd. Härma användarens tidigare meddelanden som syns på skärmen och sparade stilprov.' + styleBlock() + ' Returnera endast den förbättrade texten. Skicka aldrig, tryck aldrig Enter.' },
      { role: 'user', content: content('Aktivt utkast i skrivfältet:\n' + draft + '\n\nAnvänd skärmbilden för att förstå mottagaren och sammanhanget.', shot) },
    ], { maxTokens: 360, temperature: 0.35 });
    const suggestion = String(result.content || '').trim();
    const sig = suggestion.toLowerCase();
    if (suggestion && suggestion !== draft && lastObservedDraft === signature && mode === 'chat' && sig !== lastAutoDraftSuggestion) {
      lastAutoDraftSuggestion = sig;
      if (panel && !panel.isDestroyed()) panel.webContents.send('smart-draft', { text: suggestion, originalDraft: draft });
    }
  } catch (_) {
  } finally {
    if (panel && !panel.isDestroyed()) panel.webContents.send('smart-draft-loading', false);
    autoDraftBusy = false;
  }
}
async function pollForeground() {
  if (!panel || panel.isDestroyed() || panel.isFocused()) return;
  const r = await ps([...WIN32,
    '$h=[W32]::GetForegroundWindow(); $sb=New-Object System.Text.StringBuilder 512; [void][W32]::GetWindowText($h,$sb,512)',
    '$p=0; [void][W32]::GetWindowThreadProcessId($h,[ref]$p); $n=(Get-Process -Id $p -ErrorAction SilentlyContinue).ProcessName',
    '$v=""; $f=""; try { Add-Type -AssemblyName UIAutomationClient -ErrorAction Stop; $focused=[System.Windows.Automation.AutomationElement]::FocusedElement; $pat=$null; if($focused -and -not $focused.Current.IsPassword -and $focused.TryGetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern,[ref]$pat)){ $f=$pat.Current.Value } elseif($focused -and -not $focused.Current.IsPassword -and $focused.TryGetCurrentPattern([System.Windows.Automation.TextPattern]::Pattern,[ref]$pat)){ $f=$pat.DocumentRange.GetText(1200) }; $root=[System.Windows.Automation.AutomationElement]::FromHandle($h); $items=$root.FindAll([System.Windows.Automation.TreeScope]::Descendants,[System.Windows.Automation.Condition]::TrueCondition); $parts=New-Object System.Collections.Generic.List[string]; $i=0; foreach($x in $items){ if($i -ge 120 -or $parts.Count -ge 100){break}; $i++; try { if($x.Current.IsPassword -or $x.Current.IsOffscreen){continue}; $z=$x.Current.Name; if($z -and $z.Trim()){ $parts.Add($z.Trim()) }; $pat=$null; if($x.TryGetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern,[ref]$pat)){ $z=$pat.Current.Value; if($z -and $z.Trim()){ $parts.Add($z.Trim()) } } } catch {} }; $v=$parts -join " | "; if($v.Length -gt 6000){$v=$v.Substring(0,6000)} } catch {}',
    '$et=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($sb.ToString())); $ef=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($f)); $ev=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($v)); $et + "|" + $n + "|" + $ef + "|" + $ev'], 9000);
  if (r.ok && r.out.includes('|')) {
    const parts = r.out.trim().split('|');
    if (parts.length >= 4) {
      const decode = (v) => { try { return Buffer.from(v, 'base64').toString('utf8'); } catch { return ''; } };
      const title = decode(parts[0]), proc = parts[1].toLowerCase();
      const focusedText = decode(parts[2]), visibleText = decode(parts.slice(3).join('|'));
      if (proc && proc !== 'screenbubble' && proc !== 'electron') {
        lastExternal = { title, proc };
        queueSmartDraft(focusedText, proc, title);
        inspectForActiveHelp(title, proc, visibleText);
      }
    }
  }
}

// ------------------------------------------------------------------ windows
let panel = null, dot = null, answerWin = null, settingsWin = null, mode = 'bar', answerTimer = null, qaBusy = false;

let barOff = 0;
const SIZES = { bar: [22, 96], menu: [430, 740], chat: [410, 650] };
function layout(v) {
  const wa = screen.getPrimaryDisplay().workArea;
  let [w, h] = SIZES[v] || SIZES.bar;
  if (v === 'chat') h = Math.min(h, Math.round(wa.height * 0.78));
  if (v === 'menu') h = Math.min(h, Math.round(wa.height * 0.84));
  const cy = wa.y + wa.height / 2 + barOff;
  const y = Math.round(Math.min(Math.max(wa.y, cy - h / 2), wa.y + wa.height - h));
  panel.setBounds({ x: wa.x + wa.width - w - (v === 'bar' ? 0 : 8), y, width: w, height: h });
}
function createPanel() {
  panel = new BrowserWindow({
    x: 0, y: 0, width: SIZES.bar[0], height: SIZES.bar[1],
    frame: false, transparent: true, resizable: false, alwaysOnTop: true, show: false, hasShadow: false, skipTaskbar: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  panel.setAlwaysOnTop(true, 'screen-saver');
  panel.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  panel.on('closed', () => app.quit());
  panel.on('blur', () => { if (mode === 'menu') setMode('bar'); });
}
function openSettingsWindow() {
  if (settingsWin && !settingsWin.isDestroyed()) {
    if (settingsWin.isMinimized()) settingsWin.restore();
    settingsWin.show(); settingsWin.focus(); return;
  }
  settingsWin = new BrowserWindow({
    width: 820, height: 680, minWidth: 650, minHeight: 560,
    title: 'ScreenBubble – Inställningar',
    backgroundColor: '#0b0e16',
    autoHideMenuBar: true,
    show: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  settingsWin.loadFile(path.join(__dirname, 'renderer', 'settings.html'));
  settingsWin.once('ready-to-show', () => { settingsWin.show(); settingsWin.focus(); });
  settingsWin.on('closed', () => { settingsWin = null; });
}

function createDot() {
  const wa = screen.getPrimaryDisplay().workArea;
  const s = readJson('dot.json', null);
  dot = new BrowserWindow({
    x: s ? s.x : wa.x + wa.width - 58, y: s ? s.y : wa.y + Math.round(wa.height / 3), width: 48, height: 48,
    frame: false, transparent: true, resizable: false, alwaysOnTop: true, skipTaskbar: true, show: false,
    focusable: false, hasShadow: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  dot.setAlwaysOnTop(true, 'screen-saver');
  dot.loadFile(path.join(__dirname, 'renderer', 'dot.html'), { query: { display: assistantAppearance().display } });
}
function createAnswer() {
  answerWin = new BrowserWindow({
    width: 100, height: 30, frame: false, transparent: true, resizable: false, alwaysOnTop: true,
    skipTaskbar: true, show: false, focusable: false, hasShadow: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  answerWin.setAlwaysOnTop(true, 'screen-saver');
  answerWin.loadFile(path.join(__dirname, 'renderer', 'answer.html'));
}

let prevMode = 'bar', appReady = false;
function setMode(m) {
  if (m === 'panel') m = 'bar';
  prevMode = mode;
  mode = m;
  clearTimeout(answerTimer);
  if (answerWin) answerWin.hide();
  if (m === 'hide') {
    const was = prevMode;
    panel.hide(); dot.showInactive();
    if (was !== 'hide' && appReady) setTimeout(() => showAnswer('Hide-läge på\nHåll in pricken för sidebaren'), 250);
    return;
  }
  if (m === 'bar' && assistantAppearance().display !== 'sidebar') {
    panel.hide();
    if (dot) dot.showInactive();
    return;
  }
  if (dot) dot.hide();
  layout(m);
  panel.webContents.send('view', m);
  if (m === 'bar') panel.showInactive(); else { panel.show(); panel.focus(); }
  if (m === 'chat') panel.webContents.send('panel-shown');
}

function showAnswer(text, duration) {
  if (!answerWin) createAnswer();
  const clean = String(text).replace(/\*\*/g, '').trim();
  const lines = clean.split('\n');
  const longest = Math.max(...lines.map((l) => l.length), 1);
  const w = Math.min(360, Math.max(50, longest * 8 + 24));
  const cpl = Math.max(1, Math.floor((w - 20) / 7.6));
  const nl = lines.reduce((n, l) => n + Math.max(1, Math.ceil(l.length / cpl)), 0);
  const h = nl * 19 + 14;
  const b = (mode === 'hide' && dot) ? dot.getBounds() : panel.getBounds();
  const wa = screen.getDisplayNearestPoint({ x: b.x, y: b.y }).workArea;
  let x = b.x > wa.x + wa.width / 2 ? b.x - w : b.x + b.width;
  x = Math.min(Math.max(wa.x, x), wa.x + wa.width - w);
  const y = Math.min(Math.max(wa.y, b.y + Math.round((b.height - h) / 2)), wa.y + wa.height - h);
  answerWin.setBounds({ x, y, width: w, height: h });
  answerWin.webContents.send('answer-text', clean);
  if (mode === 'hide' && dot) dot.hide();
  answerWin.showInactive();
  clearTimeout(answerTimer);
  answerTimer = setTimeout(endAnswer, duration || Math.min(10000, 5000 + (lines.length - 1) * 1500));
}
function endAnswer() {
  clearTimeout(answerTimer);
  if (answerWin) answerWin.hide();
  if (mode === 'hide' && dot) dot.showInactive();
}

// ------------------------------------------------------------------ capture
async function captureScreen(hidePanel) {
  const wasVisible = panel && panel.isVisible();
  if (hidePanel && wasVisible) { panel.setOpacity(0); await sleep(110); }
  try {
    const d = screen.getPrimaryDisplay();
    const size = { width: Math.round(d.size.width * d.scaleFactor), height: Math.round(d.size.height * d.scaleFactor) };
    const sources = await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: size });
    const src = sources.find((s) => String(s.display_id) === String(d.id)) || sources[0];
    if (!src) throw new Error('Kunde inte fånga skärmen');
    let img = src.thumbnail;
    const sz = img.getSize();
    const scale = Math.min(1, 1920 / Math.max(sz.width, sz.height));
    if (scale < 1) img = img.resize({ width: Math.round(sz.width * scale), height: Math.round(sz.height * scale) });
    return img.toJPEG(82).toString('base64');
  } finally {
    if (hidePanel && wasVisible) panel.setOpacity(1);
  }
}

async function quickAnswer() {
  if (qaBusy) return;
  qaBusy = true;
  try {
    if (!apiKey()) { showAnswer('!'); return; }
    const shot = await captureScreen(mode === 'chat');
    showAnswer(await askOnce(shot));
  } catch (e) {
    writeJson('last-error.json', { e: String(e.message || e) });
    showAnswer('!');
  } finally { qaBusy = false; }
}

// --------------------------------------------------------- computer control
const pendingConfirms = new Map();
let confirmSeq = 0;
function askConfirm(text) {
  return new Promise((resolve) => {
    const id = ++confirmSeq;
    pendingConfirms.set(id, resolve);
    if (mode !== 'chat') setMode('chat');
    panel.webContents.send('confirm-request', { id, text });
    setTimeout(() => { if (pendingConfirms.has(id)) { pendingConfirms.delete(id); resolve(false); } }, 90000);
  });
}
ipcMain.on('confirm-reply', (_e, { id, ok }) => {
  const r = pendingConfirms.get(id);
  if (r) { pendingConfirms.delete(id); r(!!ok); }
});

async function withPanelHidden(fn) {
  // Keep the UI where it is. Only hand focus back to the app underneath so keystrokes land there.
  if (panel.isFocused()) { panel.blur(); await sleep(180); }
  return fn();
}

const sensitive = /(skicka|send|radera|delete|ta bort|remove|betala|pay|köp|buy|beställ|order|posta|post|publicera|logga ut|log out|bekräfta|confirm|submit|godkänn)/i;

async function openApp(name) {
  const n = q1(name).replace(/[*?\[\]]/g, '');
  const r = await ps([
    "$n='" + n + "'",
    "$dirs=@(\"$env:ProgramData\\Microsoft\\Windows\\Start Menu\\Programs\",\"$env:APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\")",
    "$f=Get-ChildItem $dirs -Recurse -Filter *.lnk -ErrorAction SilentlyContinue | Where-Object { $_.BaseName -like \"*$n*\" } | Sort-Object { $_.BaseName.Length } | Select-Object -First 1",
    "if ($f) { Start-Process $f.FullName; 'ok:' + $f.BaseName } else { try { Start-Process $n -ErrorAction Stop; 'ok:' + $n } catch { 'fail' } }",
  ], 15000);
  return r.ok && r.out.startsWith('ok:') ? 'Öppnade ' + r.out.slice(3) + '.' : 'Hittade ingen app som heter "' + name + '".';
}
async function sendKeys(keys) {
  const r = await ps(['Add-Type -AssemblyName System.Windows.Forms', "[System.Windows.Forms.SendKeys]::SendWait('" + q1(keys) + "')"]);
  return r.ok ? 'Skickade tangenterna.' : 'Misslyckades: ' + r.err.slice(0, 120);
}
async function typeText(text) {
  text = String(text);
  if (text.length > 900) {
    const old = clipboard.readText();
    clipboard.writeText(text);
    const r = await sendKeys('^v');
    await sleep(250);
    clipboard.writeText(old);
    return r.startsWith('Skickade') ? 'Skrev texten.' : r;
  }
  // Type in small bursts so the text visibly appears, quickly but not instantly.
  const b64 = Buffer.from(text, 'utf8').toString('base64');
  const r = await ps([
    "$t=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + b64 + "'))",
    '$t=$t -replace "`r",""',
    '$buf=""; $send={ param($x) if($x.Length -gt 0){ [System.Windows.Forms.SendKeys]::SendWait($x) } }',
    'foreach($c in $t.ToCharArray()){ $s=[string]$c; if(\'+^%~(){}[]\'.Contains($s)){ $s=\'{\'+$s+\'}\' } elseif($c -eq [char]10){ $s=\'{ENTER}\' }; $buf+=$s; if($buf.Length -ge 6 -or $c -eq [char]10){ & $send $buf; $buf=""; Start-Sleep -Milliseconds 16 } }',
    '& $send $buf',
  ], 8000 + text.length * 40);
  return r.ok ? 'Skrev texten.' : 'Misslyckades: ' + r.err.slice(0, 120);
}
async function click(fx, fy, dbl) {
  const b = screen.getPrimaryDisplay().bounds;
  const x = Math.round(b.x + Math.min(Math.max(fx, 0), 1) * b.width);
  const y = Math.round(b.y + Math.min(Math.max(fy, 0), 1) * b.height);
  const once = '[W32]::mouse_event(2,0,0,0,0); Start-Sleep -Milliseconds 40; [W32]::mouse_event(4,0,0,0,0)';
  // Glide the pointer to the target so the user can follow what happens.
  const r = await ps([...WIN32,
    '$pt=New-Object W32+PT; [void][W32]::GetCursorPos([ref]$pt); $sx=$pt.X; $sy=$pt.Y; $n=16',
    'for($i=1;$i -le $n;$i++){ $t=$i/$n; $e=$t*$t*(3-2*$t); [void][W32]::SetCursorPos([int]($sx+(' + x + '-$sx)*$e),[int]($sy+(' + y + '-$sy)*$e)); Start-Sleep -Milliseconds 14 }',
    'Start-Sleep -Milliseconds 90', once,
    dbl ? 'Start-Sleep -Milliseconds 90; ' + once : '']);
  return r.ok ? 'Klickade.' : 'Kunde inte klicka: ' + r.err.slice(0, 120);
}
async function drag(fx1, fy1, fx2, fy2) {
  const b = screen.getPrimaryDisplay().bounds;
  const px = (f, o, d) => Math.round(o + Math.min(Math.max(f, 0), 1) * d);
  const x1 = px(fx1, b.x, b.width), y1 = px(fy1, b.y, b.height), x2 = px(fx2, b.x, b.width), y2 = px(fy2, b.y, b.height);
  const r = await ps([...WIN32,
    '[void][W32]::SetCursorPos(' + x1 + ',' + y1 + ')', 'Start-Sleep -Milliseconds 120', '[W32]::mouse_event(2,0,0,0,0)', 'Start-Sleep -Milliseconds 80',
    '$n=24; for($i=1;$i -le $n;$i++){ $t=$i/$n; $e=$t*$t*(3-2*$t); [void][W32]::SetCursorPos([int](' + x1 + '+(' + x2 + '-' + x1 + ')*$e),[int](' + y1 + '+(' + y2 + '-' + y1 + ')*$e)); Start-Sleep -Milliseconds 16 }',
    'Start-Sleep -Milliseconds 100', '[W32]::mouse_event(4,0,0,0,0)']);
  return r.ok ? 'Drog.' : 'Kunde inte dra: ' + r.err.slice(0, 120);
}
async function scrollWheel(dir, amount) {
  const d = (String(dir).toLowerCase().startsWith('u') ? 1 : -1) * 120 * Math.max(1, Math.min(10, amount || 3));
  const r = await ps([...WIN32, '[W32]::mouse_event(0x0800,0,0,' + d + ',0)']);
  return r.ok ? 'Scrollade.' : 'Kunde inte scrolla.';
}

function tool(name, desc, props = {}, required = []) {
  const p = {};
  for (const [k, [type, d]] of Object.entries(props)) p[k] = { type, description: d };
  return { type: 'function', function: { name, description: desc, parameters: { type: 'object', properties: p, required } } };
}
const CONSEQ = ['boolean', 'true om handlingen skickar, raderar, betalar, publicerar eller är svår att ångra'];
const TOOLS = [
  tool('read_screen', 'Läs av aktivt fönster och ta en ny skärmbild.'),
  tool('open_app', 'Öppna ett program via namn.', { name: ['string', 'Programmets namn, t.ex. Chrome, Outlook'] }, ['name']),
  tool('open_url', 'Öppna en webbadress i standardwebbläsaren.', { url: ['string', 'Hela adressen'] }, ['url']),
  tool('type_text', 'Skriv text i det fönster som är aktivt (klistrar in).', { text: ['string', 'Texten'] }, ['text']),
  tool('press_keys', 'Tryck tangenter med SendKeys-syntax, t.ex. "{ENTER}", "^c" (Ctrl+C), "%{F4}" (Alt+F4), "{TAB}".', { keys: ['string', 'SendKeys-sträng'], consequential: CONSEQ }, ['keys']),
  tool('click', 'Klicka på en position. x och y är bråkdelar 0..1 av skärmens bredd/höjd enligt skärmbilden.', { x: ['number', '0..1 från vänster'], y: ['number', '0..1 uppifrån'], double: ['boolean', 'dubbelklick'], consequential: CONSEQ }, ['x', 'y']),
  tool('drag', 'Dra med musen från en position till en annan (fraktioner 0..1 av skärmen). För att flytta fönster, markera text eller dra filer.', { x1: ['number', 'start x'], y1: ['number', 'start y'], x2: ['number', 'slut x'], y2: ['number', 'slut y'], consequential: CONSEQ }, ['x1', 'y1', 'x2', 'y2']),
  tool('scroll', 'Scrolla med mushjulet.', { direction: ['string', 'up eller down'], amount: ['integer', 'steg 1-10'] }, ['direction']),
  tool('compose_email', 'Öppna ett mejlutkast i standardmejlprogrammet. Användaren trycker själv på skicka.', { to: ['string', 'Mottagare'], subject: ['string', 'Ämne'], body: ['string', 'Text'] }, ['subject', 'body']),
  tool('web_search', 'Sök på webben och få en sammanfattning med källor.', { query: ['string', 'Sökfråga'] }, ['query']),
  tool('remember', 'Spara ett faktum om användaren till senare.', { fact: ['string', 'Faktat'] }, ['fact']),
  tool('wait', 'Vänta några sekunder.', { seconds: ['integer', '1-10'] }, ['seconds']),
];

function systemPrompt() {
  const now = new Date().toLocaleString('sv-SE');
  const mem = memory();
  const proc = String(lastExternal.proc || '').toLowerCase();
  const title = String(lastExternal.title || '');
  const lowTitle = title.toLowerCase();
  const appProfile =
    /outlook|thunderbird|mail/.test(proc) || /gmail|outlook|inbox/.test(lowTitle) ? 'E-POST: prioritera mejlsammanfattningar, tydliga nästa steg och svar i användarens ton. Skicka aldrig utan bekräftelse.' :
    /whatsapp|telegram|discord|slack|teams|signal|messenger/.test(proc) || /whatsapp|messenger|telegram|discord|slack|teams/.test(lowTitle) ? 'MEDDELANDEN: förstå vem som skrev senast, föreslå korta naturliga svar och matcha användarens stil.' :
    /chrome|msedge|firefox|brave|opera/.test(proc) ? 'WEBB: sammanfatta aktuell sida, översätt markerad text, förklara begrepp och erbjud källbaserad research.' :
    /code|devenv|idea|studio|cursor|windsurf/.test(proc) ? 'UTVECKLING: hjälp med kod och felsökning. Var exakt och ändra inte filer utan uttrycklig begäran.' :
    /excel|winword|powerpnt/.test(proc) || /excel|word|powerpoint/.test(lowTitle) ? 'OFFICE: hjälp med formler, dokument, presentationer och struktur.' :
    'ALLMÄNT: identifiera uppgiftstypen på skärmen och välj det mest användbara arbetssättet.';
  const profileEnabled = featureSettings().appProfiles;
  return 'Du är ScreenBubble, en snabb och vänlig assistent på användarens Windows-dator, ungefär som Siri fast mycket smartare. ' +
    'Du ser skärmen (bild) och kan styra datorn med verktyg.\n' +
    (profileEnabled ? 'Aktiv app: ' + (lastExternal.proc || 'okänd') + '. Fönstertitel: ' + (lastExternal.title || 'okänd') + '. App-profil: ' + appProfile + '\nAnpassa exempel, ton och rekommenderade arbetsflöden efter denna app.\n' : '') +
    'Regler:\n' +
    '- Svara kort, naturligt och på användarens språk. Använd inte markdown.\n' +
    '- Använd verktyg bara när användaren vill att något ska göras eller du behöver mer information. Annars svara direkt.\n' +
    '- Efter varje handling får du en ny skärmbild. Kontrollera att det blev rätt innan du går vidare. Max 10 steg.\n' +
    '- Sätt consequential=true när en handling skickar, raderar, betalar, publicerar eller ändrar något som är svårt att ångra. Användaren får då bekräfta.\n' +
    '- compose_email öppnar bara ett utkast. Användaren trycker själv på skicka.\n' +
    '- Fråga användaren om något viktigt saknas. Gör aldrig något användaren inte bett om. Nekar användaren en bekräftelse: sluta och säg det kort.\n' +
    '- Att googla: använd open_url med https://www.google.com/search?q=... så användaren ser sökningen på skärmen. Använd web_search (i bakgrunden) bara när användaren vill ha ett sammanfattat svar eller research.\n' +
    '- Gör saker synligt på skärmen (öppna appar, skriv i fönster) och inte i bakgrunden.\n' +
    '- Använd remember när användaren ber dig komma ihåg något.\nNu: ' + now + '\n' +
    (mem.length ? 'Det du vet om användaren (använd naturligt, nämn det inte i onödan):\n' + mem.map((m) => '- ' + m).join('\n') : '') + styleBlock();
}

function send2(ch, v) { if (panel && !panel.isDestroyed()) panel.webContents.send(ch, v); }
function status(t) { if (panel && !panel.isDestroyed()) panel.webContents.send('status', t); }

async function runAgent(userText) {
  const messages = [{ role: 'system', content: systemPrompt() }, ...history()];
  const shot0 = await captureScreen(true);
  messages.push({
    role: 'user',
    content: content('Användarens meddelande: ' + userText + '\n\nAktivt fönster: ' + lastExternal.title + ' (' + lastExternal.proc + ')', shot0),
  });
  let answer = '';
  for (let step = 0; step < 10; step++) {
    const msg = await chatStream(messages, { tools: TOOLS, maxTokens: 900 }, (t) => send2('delta', t), () => send2('delta-reset'));
    const calls = msg.tool_calls;
    if (!calls || !calls.length) { answer = (msg.content || '').trim(); break; }
    messages.push({ role: 'assistant', content: msg.content || '', tool_calls: calls });
    let newShot = null;
    for (const c of calls) {
      let args = {};
      try { args = JSON.parse(c.function.arguments || '{}'); } catch {}
      let result;
      try {
        const out = await execTool(c.function.name, args);
        result = out.text;
        if (out.shot) newShot = out.shot;
      } catch (e) { result = 'Fel: ' + e.message; }
      messages.push({ role: 'tool', tool_call_id: c.id, content: result });
    }
    if (newShot) messages.push({ role: 'user', content: content('Ny skärmbild efter åtgärden.', newShot) });
  }
  if (!answer) answer = 'Jag hann inte klart. Försök igen eller dela upp uppgiften.';
  addHistory('user', userText);
  addHistory('assistant', answer);
  learn(userText, answer);
  return answer;
}

async function acted(text) {
  await sleep(700);
  return { text, shot: await captureScreen(false) };
}

async function execTool(name, a) {
  switch (name) {
    case 'read_screen':
      status('Läser skärmen…');
      return { text: 'Aktivt fönster: ' + lastExternal.title + ' (' + lastExternal.proc + ')', shot: await captureScreen(true) };
    case 'open_app':
      status('Öppnar ' + a.name + '…');
      return acted(await withPanelHidden(() => openApp(String(a.name || ''))));
    case 'open_url': {
      const u = String(a.url || '');
      const url = /^https?:\/\//i.test(u) ? u : 'https://' + u;
      status('Öppnar ' + url + '…');
      await shell.openExternal(url);
      return acted('Öppnade ' + url + '.');
    }
    case 'type_text':
      status('Skriver…');
      return acted(await withPanelHidden(() => typeText(String(a.text || ''))));
    case 'press_keys':
      if (a.consequential && !(await askConfirm('Trycka "' + a.keys + '"?')))
        return { text: 'Användaren nekade åtgärden.' };
      status('Trycker tangenter…');
      return acted(await withPanelHidden(() => sendKeys(String(a.keys || ''))));
    case 'click':
      if (a.consequential && !(await askConfirm('Klicka på markerad plats?'))) return { text: 'Användaren nekade åtgärden.' };
      status('Klickar…');
      return acted(await withPanelHidden(() => click(Number(a.x), Number(a.y), !!a.double)));
    case 'drag':
      if (a.consequential && !(await askConfirm('Dra på skärmen?'))) return { text: 'Användaren nekade åtgärden.' };
      status('Drar…');
      return acted(await withPanelHidden(() => drag(Number(a.x1), Number(a.y1), Number(a.x2), Number(a.y2))));
    case 'scroll':
      status('Scrollar…');
      return acted(await withPanelHidden(() => scrollWheel(a.direction, a.amount)));
    case 'compose_email': {
      status('Förbereder mejl…');
      const url = 'mailto:' + encodeURIComponent(a.to || '') + '?subject=' + encodeURIComponent(a.subject || '') + '&body=' + encodeURIComponent(a.body || '');
      await shell.openExternal(url);
      return acted('Mejlutkast öppnat. Användaren trycker själv på skicka.');
    }
    case 'web_search':
      status('Söker: ' + a.query);
      return { text: await webSearch(String(a.query || '')) };
    case 'remember':
      addMemory(a.fact, true);
      return { text: 'Sparat.' };
    case 'wait':
      await sleep(Math.min(10, Math.max(1, Number(a.seconds) || 1)) * 1000);
      return { text: 'Väntade.' };
  }
  return { text: 'Okänt verktyg: ' + name };
}

// --------------------------------------------------------------------- IPC
let busy = false;
async function fastCommand(t) {
  const s = String(t || '').trim();
  if (/sammanfatta|jämför|förklara|berätta|research|och sedan|och sen/i.test(s)) return null;
  let m = s.match(/^(?:google|googla|sök(?: efter| på)?(?: google)?)\s+(.+)$/i);
  if (m) { await shell.openExternal('https://www.google.com/search?q=' + encodeURIComponent(m[1].trim())); return 'Googlar: ' + m[1].trim(); }
  m = s.match(/^(?:öppna|starta)\s+(.+)$/i);
  if (m) return await openApp(m[1].trim());
  return null;
}
ipcMain.handle('chat', async (_e, { text }) => {
  const mc = Lib.parseMemoryCommand(text, memory());
  if (mc) { if (mc.mem) writeJson('memory.json', mc.mem); return { ok: true, reply: mc.reply }; }
  try { const f = await fastCommand(text); if (f) return { ok: true, reply: f }; } catch (_) {}
  if (busy) return { ok: false, error: 'Jag jobbar fortfarande med förra uppgiften.' };
  if (!apiKey()) return { ok: false, error: 'Ingen API-nyckel. Skriv in den under inställningar.' };
  busy = true;
  try { return { ok: true, reply: await runAgent(String(text || '')) }; }
  catch (e) { return { ok: false, error: String(e.message || e) }; }
  finally { busy = false; }
});
ipcMain.handle('chips', () => ({ title: lastExternal.title, proc: lastExternal.proc, smartReplyAllowed: smartReplyAllowed(lastExternal.proc, lastExternal.title) }));
ipcMain.handle('reply', async () => {
  try {
    if (!apiKey()) throw new Error('Ingen API-nyckel');
    if (!smartReplyAllowed(lastExternal.proc, lastExternal.title)) throw new Error('Smart Reply är inte aktiverat för den här appen.');
    const shot = await captureScreen(true);
    const t = await chat([
      { role: 'system', content: 'Du är ScreenBubbles Smart Reply. Inspektera skärmen noggrant. Om ett skrivfält innehåller ett pågående utkast från användaren: förbättra och färdigställ just det utkastet utan att ändra avsikten eller hitta på fakta och sätt mode=draft. Om inget utkast skrivs: läs det senaste inkommande mejlet eller meddelandet och skriv ett passande svar och sätt mode=reply. Avgör om det är mejl, chatt eller ett mer formellt samtal och anpassa ton, hälsning och längd. Lär av användarens tidigare skickade meddelanden som syns i konversationen och använd sparade stilprov om sådana finns. Returnera JSON med exakt fälten mode och text. Skicka aldrig meddelandet, tryck aldrig Enter.' },
      { role: 'user', content: content('Föreslå ett svar eller förbättra användarens synliga utkast.', shot) },
    ], { maxTokens: 600, json: true });
    const parsed = JSON.parse(t.content || '{}');
    return { ok: true, text: String(parsed.text || '').trim(), mode: parsed.mode === 'draft' ? 'draft' : 'reply' };
  } catch (e) { return { ok: false, error: String(e.message || e) }; }
});
ipcMain.handle('transform', async (_e, { instruction, text }) => {
  try {
    return { ok: true, text: await textCompletion('Du redigerar text åt användaren. Svara ENDAST med den nya texten, utan inledning eller citattecken.', 'Instruktion: ' + instruction + '\n\nText:\n' + text, 600) };
  } catch (e) { return { ok: false, error: String(e.message || e) }; }
});
ipcMain.handle('insert', async (_e, { text, send, replace }) => {
  if (replace && !send) autoDraftSuppressedUntil = Date.now() + 5000;
  if (send && !(await askConfirm('Skicka det här meddelandet?\n\n' + text))) return { ok: false, error: 'Avbrutet.' };
  const r = await withPanelHidden(async () => {
    if (replace && !send) await sendKeys('^a');
    const t = await typeText(text);
    if (send) { await sleep(300); await sendKeys('{ENTER}'); }
    return t;
  });
  return { ok: true, text: r + (send ? ' Tryckte Enter.' : '') };
});
ipcMain.handle('open-link', (_e, u) => { if (/^https?:\/\//i.test(String(u))) shell.openExternal(String(u)); return true; });
ipcMain.handle('copy', (_e, t) => { clipboard.writeText(String(t || '')); return true; });
ipcMain.handle('new-chat', () => { writeJson('history.json', []); curConv = null; return true; });
ipcMain.handle('archive-list', (_e, q) => Lib.searchArchive(readJson('archive.json', []), q, 100));
ipcMain.handle('archive-open', (_e, id) => {
  const c = readJson('archive.json', []).find((x) => x.id === id);
  if (!c) return null;
  curConv = c.id;
  writeJson('history.json', c.messages.slice(-24).map((x) => ({ role: x.role, content: x.content })));
  return c.messages;
});
ipcMain.handle('archive-delete', (_e, id) => { writeJson('archive.json', readJson('archive.json', []).filter((x) => x.id !== id)); if (curConv === id) curConv = null; return true; });
ipcMain.handle('archive-clear', () => { writeJson('archive.json', []); curConv = null; return true; });
ipcMain.handle('commands-get', () => commands());
ipcMain.handle('commands-save', (_e, c) => {
  const n = Lib.sanitizeCommand(c); if (!n) return commands();
  const list = commands(); const i = list.findIndex((x) => x.id === n.id);
  if (i >= 0) list[i] = n; else list.push(n);
  writeJson('commands.json', list.slice(0, 24)); return list.slice(0, 24);
});
ipcMain.handle('commands-delete', (_e, id) => { const l = commands().filter((x) => x.id !== id); writeJson('commands.json', l); return l; });
ipcMain.handle('style-add', (_e, t) => { writeJson('style.json', Lib.addStyleSample(styleSamples(), t)); return true; });
ipcMain.handle('memory-get', () => memory());
ipcMain.handle('memory-set', (_e, list) => { writeJson('memory.json', Array.isArray(list) ? list.map(String).slice(0, 40) : []); return true; });
ipcMain.handle('memory-auto-get', () => readJson('settings.json', {}).memoryAuto === true);
ipcMain.handle('memory-auto-set', (_e, on) => { const s = readJson('settings.json', {}); s.memoryAuto = !!on; writeJson('settings.json', s); return !!s.memoryAuto; });
ipcMain.handle('open-settings', () => { openSettingsWindow(); return true; });
ipcMain.handle('features-get', () => featureSettings());
ipcMain.handle('appearance-get', () => assistantAppearance());
ipcMain.handle('appearance-set', (_e, next) => {
  const saved = readJson('settings.json', {});
  saved.assistantDisplay = ['sidebar', 'bubble', 'swipe'].includes(next?.display) ? next.display : 'sidebar';
  saved.assistantSwipe = next?.swipe === 'right' ? 'right' : 'left';
  writeJson('settings.json', saved);
  if (dot && !dot.isDestroyed()) dot.loadFile(path.join(__dirname, 'renderer', 'dot.html'), { query: { display: saved.assistantDisplay } });
  if (mode === 'bar') setMode('bar');
  return assistantAppearance();
});
ipcMain.handle('smart-reply-apps-get', () => ({ apps: smartReplyApps(), options: SMART_REPLY_APPS }));
ipcMain.handle('smart-reply-apps-set', (_e, apps) => {
  const clean = Array.isArray(apps) ? [...new Set(apps.filter((x) => Object.prototype.hasOwnProperty.call(SMART_REPLY_APPS, x)))] : [];
  const s = readJson('settings.json', {}); s.smartReplyApps = clean; writeJson('settings.json', s);
  send2('features-updated', featureSettings());
  return { apps: clean, options: SMART_REPLY_APPS };
});
ipcMain.handle('features-set', (_e, next) => {
  const s = readJson('settings.json', {});
  const clean = { ...FEATURE_DEFAULTS };
  if (next && typeof next === 'object') for (const k of Object.keys(clean)) if (typeof next[k] === 'boolean') clean[k] = next[k];
  s.features = clean; writeJson('settings.json', s);
  if (clean.selectionAsk) globalShortcut.register('Control+Alt+C', explainSelection);
  else globalShortcut.unregister('Control+Alt+C');
  send2('features-updated', clean);
  if (settingsWin && !settingsWin.isDestroyed()) settingsWin.webContents.send('features-updated', clean);
  return clean;
});
ipcMain.handle('set-mode', (_e, m) => { setMode(m); return true; });
ipcMain.on('bar-move', (_e, { y }) => {
  const wa = screen.getPrimaryDisplay().workArea;
  barOff = Math.round(Math.max(-wa.height / 2 + 60, Math.min(wa.height / 2 - 60, y - (wa.y + wa.height / 2))));
  layout(mode === 'hide' ? 'bar' : mode);
});
ipcMain.handle('quick', async () => { setMode('bar'); await sleep(150); quickAnswer(); return true; });
ipcMain.handle('quit', () => { app.quit(); return true; });
ipcMain.handle('settings-get', () => ({ hasEmbedded: !!embeddedKey(), hasKey: !!apiKey() }));
ipcMain.handle('settings-set', (_e, s) => { const cur = readJson('settings.json', {}); if (s && typeof s.apiKey === 'string') cur.apiKey = s.apiKey.trim(); writeJson('settings.json', cur); return true; });
ipcMain.handle('stt', async (_e, buf) => {
  try {
    const fd = new FormData();
    fd.append('file', new Blob([Buffer.from(buf)], { type: 'audio/webm' }), 'audio.webm');
    fd.append('model', 'gpt-4o-mini-transcribe');
    const lang = (app.getLocale() || '').slice(0, 2);
    if (lang) fd.append('language', lang);
    const r = await fetch('https://api.openai.com/v1/audio/transcriptions', {
      method: 'POST', headers: { Authorization: 'Bearer ' + apiKey() }, body: fd, signal: AbortSignal.timeout(60000),
    });
    const j = await r.json();
    if (!r.ok) throw new Error((j.error && j.error.message) || 'Taligenkänning misslyckades');
    return { ok: true, text: (j.text || '').trim() };
  } catch (e) { return { ok: false, error: String(e.message || e) }; }
});

// dot + answer
let lastDotClick = 0;
ipcMain.on('dot-click', () => {
  lastDotClick = Date.now();
  setMode('menu');
});
ipcMain.on('dot-dbl', () => setMode('chat'));
ipcMain.on('dot-panel', () => setMode('menu'));
ipcMain.on('dot-swipe', (_e, direction) => {
  const cfg = assistantAppearance();
  setMode(direction === cfg.swipe ? 'menu' : 'chat');
});
ipcMain.on('dot-move', (_e, { x, y }) => { if (dot) dot.setPosition(Math.round(x), Math.round(y)); });
ipcMain.on('dot-save', () => { if (dot) { const b = dot.getBounds(); writeJson('dot.json', { x: b.x, y: b.y }); } });
ipcMain.on('answer-click', () => {
  if (activeHelpPrompt) {
    const prompt = activeHelpPrompt; activeHelpPrompt = null; endAnswer(); setMode('chat');
    setTimeout(() => send2('ask', prompt), 220);
  } else endAnswer();
});

// ---------------------------------------------------------------- lifecycle
app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler((_wc, permission, cb) => cb(permission === 'media'));
  session.defaultSession.setPermissionCheckHandler((_wc, permission) => permission === 'media');
  createPanel();
  createDot();
  createAnswer();
  panel.webContents.once('did-finish-load', () => setMode(readJson('mode.json', { m: 'bar' }).m === 'hide' ? 'hide' : 'bar'));
  globalShortcut.register('Control+Alt+Space', () => setMode(mode === 'bar' ? 'menu' : mode === 'hide' ? 'bar' : 'bar'));
  globalShortcut.register('Control+Alt+A', () => quickAnswer());
  setInterval(pollForeground, 2500);
  if (featureSettings().selectionAsk) globalShortcut.register('Control+Alt+C', explainSelection);
  makeTray();
  initUpdater();
  setTimeout(() => { appReady = true; }, 1500);
});

// ------------------------------------------------------------------ auto-update
let updater = null, updateState = { state: 'idle' };
function pushUpdate(s) { updateState = s; send2('update', s); if (tray) try { tray.setToolTip(s.state === 'ready' ? 'ScreenBubble – ny version redo' : 'ScreenBubble'); } catch {} }
function initUpdater() {
  if (!app.isPackaged) return;
  try {
    updater = require('electron-updater').autoUpdater;
    updater.autoDownload = true;
    updater.autoInstallOnAppQuit = true;
    updater.logger = null;
    updater.on('update-available', (i) => pushUpdate({ state: 'downloading', version: i.version }));
    updater.on('update-not-available', () => pushUpdate({ state: 'latest' }));
    updater.on('update-downloaded', (i) => {
      pushUpdate({ state: 'ready', version: i.version });
      // The user has enabled automatic updates: install as soon as the download is complete.
      setTimeout(() => { try { updater.quitAndInstall(true, true); } catch {} }, 1200);
    });
    updater.on('error', () => pushUpdate({ state: 'idle' }));
    setTimeout(checkUpdate, 15000);
    setInterval(checkUpdate, 4 * 3600 * 1000);
  } catch { updater = null; }
}
function checkUpdate() { if (!updater) return Promise.resolve(); pushUpdate({ state: 'checking' }); return updater.checkForUpdates().catch(() => pushUpdate({ state: 'idle' })); }
ipcMain.handle('update-state', () => ({ ...updateState, version: app.getVersion(), packaged: app.isPackaged }));
ipcMain.handle('update-check', () => { checkUpdate(); return true; });
ipcMain.handle('update-install', () => { if (updater && updateState.state === 'ready') { setImmediate(() => updater.quitAndInstall(true, true)); } return true; });

// ------------------------------------------------------------ tray, autostart, selection
const TRAY_PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAABBElEQVR4nO3XVw6CQBSFYXdo712XICj2ujj3YgUF8emaKyExRmYYnLkkxknOq99vgCiJxP8IHgDYsqYcXXdcWLddWOFaN1jimg4scA0HpMf4H7bp3oGHzxs2zOs2zGpX+DriFRbFcdMq7hItRBY+qeAs8QiZ+KRswbjkRcSGj0smjIomPyLqDRcGHxXP/EuhEh8WvHEDVOJG/hQcQIEbuRMMcsfPEc8AAnyQZQRQ4H1mAAHezxyCAyhwnRlAgOvpfXAABa4xAwhwLRUQ4EfEhvsBKvFecscPwKnEQ/0iqsC53/49Ijb89VLIxCP/L4wF/xSi5IYTjcCFec7J3pLIXs1++jwA1IEz2SC+hVgAAAAASUVORK5CYII=';
let tray = null;
const autostart = () => { try { return app.getLoginItemSettings().openAtLogin; } catch { return false; } };
function setAutostart(on) { try { app.setLoginItemSettings({ openAtLogin: !!on }); } catch {} return autostart(); }
function makeTray() {
  try {
    tray = new Tray(nativeImage.createFromDataURL(TRAY_PNG));
    tray.setToolTip('ScreenBubble');
    const build = () => Menu.buildFromTemplate([
      { label: 'Öppna meny', click: () => setMode('menu') },
      { label: 'Visa sidebar', click: () => setMode('bar') },
      { label: 'Snabbsvar på skärmen', click: () => quickAnswer() },
      { label: 'Förklara markerad text (Ctrl+Alt+C)', click: () => explainSelection() },
      { type: 'separator' },
      { label: updateState.state === 'ready' ? 'Starta om och uppdatera' : 'Sök efter uppdatering', click: () => (updateState.state === 'ready' && updater ? updater.quitAndInstall(true, true) : checkUpdate()) },
      { label: 'Starta med Windows', type: 'checkbox', checked: autostart(), click: (i) => setAutostart(i.checked) },
      { label: 'Avsluta', click: () => app.quit() },
    ]);
    tray.on('click', () => setMode('menu'));
    tray.on('right-click', () => tray.popUpContextMenu(build()));
  } catch {}
}
// Copies the current selection in whatever app is in front and asks about it in the chat.
async function explainSelection() {
  const old = clipboard.readText();
  clipboard.writeText('');
  await sendKeys('^c');
  await sleep(180);
  const sel = clipboard.readText().trim();
  if (old) clipboard.writeText(old);
  if (!sel) { showAnswer('Markera text först'); return; }
  setMode('chat');
  setTimeout(() => send2('ask', 'Förklara eller svara kort på den här texten som jag markerade:\n\n' + sel.slice(0, 4000)), 350);
}
ipcMain.handle('autostart-get', () => autostart());
ipcMain.handle('autostart-set', (_e, on) => setAutostart(on));
app.on('before-quit', () => writeJson('mode.json', { m: mode === 'hide' ? 'hide' : 'bar' }));
app.on('will-quit', () => globalShortcut.unregisterAll());
app.on('window-all-closed', () => app.quit());
app.on('second-instance', () => setMode('menu'));

app.on('will-quit', () => { try { if (pss) pss.p.kill(); } catch {} });
