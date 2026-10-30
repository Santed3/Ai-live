const $ = (id) => document.getElementById(id);
const meta = [
 ['smartReply','💬','Smart Reply','Förstår mejl, chattar och utkast. Lär sig av formuleringar du väljer att använda.'],
 ['contextualSuggestions','✨','Smarta förslag','Visar relevanta snabbval beroende på vilken app du använder.'],
 ['screenActions','⚡','AI Actions','Hjälper dig utföra uppgifter i flera steg på skärmen.'],
 ['smartTranslate','🌐','Smart Translate','Översätter text direkt i appar och behåller sammanhanget.'],
 ['studyMode','🎓','Study Mode','Förklarar frågor och uppgifter steg för steg.'],
 ['writingMode','✍️','Writing Mode','Skriver, förbättrar och anpassar texter efter din ton.'],
 ['webResearch','🔎','Web Research','Söker, jämför källor och sammanfattar ämnen.'],
 ['appProfiles','🧩','App Profiles','Anpassar förslag efter aktiv app och arbetsflöde.'],
 ['selectionAsk','🖱️','Markera och fråga','Markera text och tryck Ctrl + Alt + C för att fråga om den.'],
 ['voice','🎙','Röstassistent','Aktiverar mikrofon och samtal i realtid.'],
];
let features = {}, timer, smartReplyConfig = { apps: [], options: {} };
function toast(){const t=$('toast');t.classList.add('show');clearTimeout(timer);timer=setTimeout(()=>t.classList.remove('show'),1300)}
async function saveFeature(key,value){features[key]=value;$('saveState').textContent='● Sparar…';features=await api.featuresSet(features);$('saveState').textContent='● Sparas automatiskt';toast()}
function renderSmartReplyApps(){
 const box=$('smartReplyAppList'); if(!box) return; box.textContent='';
 for(const [key,label] of Object.entries(smartReplyConfig.options||{})){
  const row=document.createElement('label');row.className='feature';
  const ico=document.createElement('span');ico.className='ico';ico.textContent='💬';
  const copy=document.createElement('span');copy.className='copy';
  const b=document.createElement('b');b.textContent=label;
  const small=document.createElement('small');small.textContent='Tillåt Smart Reply i den här appen.';
  copy.append(b,small);
  const sw=document.createElement('input');sw.type='checkbox';sw.className='switch';sw.checked=(smartReplyConfig.apps||[]).includes(key);
  sw.onchange=async()=>{const set=new Set(smartReplyConfig.apps||[]);if(sw.checked)set.add(key);else set.delete(key);smartReplyConfig=await api.smartReplyAppsSet([...set]);toast();};
  row.append(ico,copy,sw);box.append(row);
 }
}
function render(){
 const box=$('featureList');box.textContent='';
 for(const [key,icon,label,desc] of meta){
  const row=document.createElement('label');row.className='feature';
  const ico=document.createElement('span');ico.className='ico';ico.textContent=icon;
  const copy=document.createElement('span');copy.className='copy';
  const b=document.createElement('b');b.textContent=label;const small=document.createElement('small');small.textContent=desc;copy.append(b,small);
  const sw=document.createElement('input');sw.type='checkbox';sw.className='switch';sw.checked=!!features[key];sw.onchange=()=>saveFeature(key,sw.checked);
  row.append(ico,copy,sw);box.append(row);
 }
 $('readAloud').checked=!!features.readAloud;
}
(async()=>{
 features=await api.featuresGet();render();
 smartReplyConfig=await api.smartReplyAppsGet();renderSmartReplyApps();
 $('readAloud').onchange=()=>saveFeature('readAloud',$('readAloud').checked);
 $('memoryAuto').checked=await api.memoryAutoGet();
 $('memoryAuto').onchange=async()=>{await api.memoryAutoSet($('memoryAuto').checked);toast()};
 $('autostart').checked=await api.autostartGet();
 $('autostart').onchange=async()=>{await api.autostartSet($('autostart').checked);toast()};
 $('version').textContent='WINDOWS · '+(await api.updateState()).version;
 for(const b of document.querySelectorAll('nav button'))b.onclick=()=>{document.querySelectorAll('nav button').forEach(x=>x.classList.remove('active'));b.classList.add('active');$(b.dataset.target).scrollIntoView({behavior:'smooth',block:'start'})};
})();
