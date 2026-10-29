// Pure helpers shared by the main process (require) and the renderer (<script>). No Electron or DOM access here.
(function (root, factory) {
  if (typeof module !== 'undefined' && module.exports) module.exports = factory();
  else root.Lib = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const SENSITIVE = /(lösenord|password|passwd|pinkod|\bpin\b|personnummer|kortnummer|kreditkort|credit card|\bcvv\b|\biban\b|bankid|kontonummer|diagnos|sjukdom|sjuk\b|medicin|recept|läkare|hälsa|ångest|depress|graviditet|religion|\b\d{9,}\b|\d{6}[-+]?\d{4})/i;
  const isSensitive = (s) => SENSITIVE.test(String(s || ''));
  const normalize = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9åäö ]+/g, ' ').replace(/\s+/g, ' ').trim();

  function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
  // Escapes text and wraps every occurrence of the search words in <mark>.
  function highlightHtml(text, q) {
    const words = String(q || '').toLowerCase().split(/\s+/).filter(Boolean)
      .map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    const safe = escapeHtml(text);
    if (!words.length) return safe;
    const re = new RegExp('(' + words.map(escapeHtml).join('|') + ')', 'gi');
    return safe.replace(re, '<mark>$1</mark>');
  }

  function titleFrom(text) {
    const t = String(text || '').replace(/\s+/g, ' ').trim();
    if (!t) return 'Ny konversation';
    return t.length > 48 ? t.slice(0, 47).trimEnd() + '…' : t;
  }
  function makeSnippet(text, word) {
    const t = String(text || '').replace(/\s+/g, ' ');
    const i = t.toLowerCase().indexOf(String(word || '').toLowerCase());
    if (i < 0) return t.slice(0, 80);
    const a = Math.max(0, i - 30), b = Math.min(t.length, i + word.length + 50);
    return (a > 0 ? '…' : '') + t.slice(a, b) + (b < t.length ? '…' : '');
  }

  // Every word must occur in the title or somewhere in the conversation.
  function searchArchive(arr, q, limit) {
    const words = String(q || '').toLowerCase().split(/\s+/).filter(Boolean);
    const res = [];
    for (const c of arr || []) {
      const texts = (c.messages || []).map((m) => String(m.content || ''));
      const hay = (String(c.title || '') + '\n' + texts.join('\n')).toLowerCase();
      if (!words.every((w) => hay.includes(w))) continue;
      let snippet = '';
      if (words.length) {
        const t = texts.find((x) => x.toLowerCase().includes(words[0]));
        if (t) snippet = makeSnippet(t, words[0]);
      }
      res.push({ id: c.id, title: c.title, ts: c.ts, count: texts.length, snippet });
    }
    res.sort((a, b) => b.ts - a.ts);
    return res.slice(0, limit || 100);
  }

  // Appends a message to conversation `id` (creates it when missing). Caps: 80 messages, 300 conversations.
  function archiveAppend(arr, id, role, content, now) {
    const list = Array.isArray(arr) ? arr.slice() : [];
    let i = id ? list.findIndex((c) => c.id === id) : -1;
    let conv;
    if (i < 0) {
      id = String(now) + Math.random().toString(36).slice(2, 6);
      conv = { id, title: role === 'user' ? titleFrom(content) : 'Ny konversation', ts: now, messages: [] };
    } else {
      conv = list.splice(i, 1)[0];
    }
    if (conv.title === 'Ny konversation' && role === 'user') conv.title = titleFrom(content);
    conv.messages.push({ role, content: String(content), ts: now });
    if (conv.messages.length > 80) conv.messages = conv.messages.slice(-80);
    conv.ts = now;
    list.push(conv);
    return { arr: list.length > 300 ? list.slice(-300) : list, id: conv.id };
  }

  // Memory: short Swedish sentences, deduplicated, max `max`. `force` skips the sensitive-content filter (explicit "remember").
  function mergeMemory(old, incoming, max, force) {
    const out = (old || []).map(String).filter(Boolean);
    const norm = out.map(normalize);
    for (let f of incoming || []) {
      f = String(f || '').replace(/\s+/g, ' ').trim().slice(0, 160);
      if (f.length < 6 || (!force && isSensitive(f))) continue;
      const n = normalize(f);
      if (!n) continue;
      const d = norm.findIndex((o) => o === n || o.includes(n) || n.includes(o));
      if (d >= 0) {
        if (n.length > norm[d].length) { out[d] = f; norm[d] = n; }
        continue;
      }
      out.push(f); norm.push(n);
    }
    return out.slice(-(max || 40));
  }

  // "visa minne", "glöm 3", "glöm allt": handled locally without any API call.
  function parseMemoryCommand(text, mem) {
    const s = String(text || '').trim();
    mem = mem || [];
    if (/^(?:visa|lista|öppna)\s+(?:mitt\s+|ditt\s+)?minne(?:t)?$/i.test(s) || /^vad\s+(?:minns|vet)\s+du(?:\s+om\s+mig)?\??$/i.test(s)) {
      if (!mem.length) return { reply: 'Jag har inte lärt mig något om dig ännu. Jag lär mig av våra samtal, och du kan säga "kom ihåg …".' };
      return { reply: 'Det här minns jag (' + mem.length + '):\n' + mem.map((m, i) => (i + 1) + '. ' + m).join('\n') + '\n\nSäg "glöm 3" eller "glöm allt" för att ta bort.' };
    }
    if (/^glöm\s+allt$/i.test(s)) return { reply: mem.length ? 'Klart, jag har glömt allt (' + mem.length + ' saker).' : 'Jag hade inget att glömma.', mem: [] };
    const m = s.match(/^glöm\s+(\d+)$/i);
    if (m) {
      const i = parseInt(m[1], 10) - 1;
      if (i < 0 || i >= mem.length) return { reply: 'Det finns inget minne med nummer ' + m[1] + '. Säg "visa minne" för listan.' };
      const next = mem.slice(); const gone = next.splice(i, 1)[0];
      return { reply: 'Glömde: ' + gone, mem: next };
    }
    return null;
  }

  // Custom quick commands.
  function sanitizeCommand(c) {
    if (!c || typeof c !== 'object') return null;
    const name = String(c.name || '').replace(/\s+/g, ' ').trim().slice(0, 30);
    const prompt = String(c.prompt || '').trim().slice(0, 800);
    if (!name || !prompt) return null;
    const emoji = Array.from(String(c.emoji || '').trim())[0] || '⚡';
    const id = typeof c.id === 'string' && /^[\w-]{1,40}$/.test(c.id) ? c.id : 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5);
    return { id, name, emoji, prompt, screen: c.screen !== false };
  }

  // Style samples: the 8 most recent drafts the user copied or sent, sensitive ones excluded.
  function addStyleSample(list, text) {
    const t = String(text || '').trim().slice(0, 400);
    if (t.length < 15 || isSensitive(t)) return list || [];
    const out = (list || []).filter((x) => x !== t);
    out.push(t);
    return out.slice(-8);
  }

  return { isSensitive, normalize, escapeHtml, highlightHtml, titleFrom, makeSnippet, searchArchive, archiveAppend, mergeMemory, parseMemoryCommand, sanitizeCommand, addStyleSample };
});
