
const STORAGE_KEY = "boloBoardStateV1";
const END_DATE = new Date("2100-12-31T23:59:59");

const defaultState = {
  profile: {
    name: "",
    hourlyWage: "",
    supplementalPay: "",
    pattern: "alpha",
    patternStart: "2026-07-13",
    payPeriodStart: "2026-07-13",
    firstPayday: "2026-07-28",
    startVacation: "",
    annualVacation: "",
    startSick: "",
    annualSick: "",
    startComp: ""
  },
  overrides: {},
  bankAdjustments: []
};

let state = loadState();
let currentMonth = new Date();
currentMonth.setDate(1);
let selectedDate = null;
let deferredPrompt = null;

const $ = id => document.getElementById(id);
const pad = n => String(n).padStart(2,"0");
const dateKey = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
const parseDate = s => {
  if (!s) return null;
  const [y,m,d] = s.split("-").map(Number);
  return new Date(y,m-1,d);
};
const addDays = (d,n) => { const x = new Date(d); x.setDate(x.getDate()+n); return x; };
const diffDays = (a,b) => Math.floor((Date.UTC(a.getFullYear(),a.getMonth(),a.getDate())-Date.UTC(b.getFullYear(),b.getMonth(),b.getDate()))/86400000);
const money = n => new Intl.NumberFormat("en-US",{style:"currency",currency:"USD"}).format(Number(n)||0);
const hoursFmt = n => `${(Number(n)||0).toFixed(2).replace(/\.00$/,"")} hrs`;

function loadState(){
  try{
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return saved ? {...defaultState,...saved,profile:{...defaultState.profile,...saved.profile}} : structuredClone(defaultState);
  }catch(e){ return structuredClone(defaultState); }
}
function saveState(){ localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); }
function toast(msg){ const t=$("toast"); t.textContent=msg; t.classList.add("show"); setTimeout(()=>t.classList.remove("show"),2200); }

const patterns = {
  alpha: ["day","day","off","off","day","day","day","off","off","day","day","off","off","off"],
  bravo: ["off","off","day","day","off","off","off","day","day","off","off","day","day","day"]
};

function scheduledEntry(date){
  const p = state.profile;
  const start = parseDate(p.patternStart);
  if(!start || date < start || date > END_DATE) return {type:"off",hours:0,source:"pattern"};
  const index = ((diffDays(date,start)%14)+14)%14;
  if(p.pattern==="alpha" || p.pattern==="bravo"){
    const type = patterns[p.pattern][index];
    return {type,hours:type==="off"?0:12,source:"pattern"};
  }
  const base = p.pattern==="charlie" ? patterns.alpha : patterns.bravo;
  const fortnight = Math.floor(diffDays(date,start)/14);
  const shiftType = (fortnight % 2 === 0) ? "night" : "day";
  const isWork = base[index] !== "off";
  return {type:isWork?shiftType:"off",hours:isWork?12:0,source:"pattern"};
}

function getEntry(date){
  const key=dateKey(date);
  const override=state.overrides[key];
  if(override && override.type!=="default") return {...override,source:"override"};
  return scheduledEntry(date);
}

const meta = {
  day:{label:"Day Shift",icon:"☀️👮"},
  night:{label:"Night Shift",icon:"🌙👮"},
  off:{label:"Off Duty",icon:"🚓"},
  extra_ot:{label:"Extra Shift",icon:"🚨💵"},
  extra_comp:{label:"Comp Shift",icon:"🚨⏱️"},
  vacation:{label:"On Vacation",icon:"🚓🏖️"},
  sick:{label:"Sick Leave",icon:"🤒🛡️"},
  comp_taken:{label:"Comp Taken",icon:"⏱️🏠"},
  court:{label:"Court",icon:"⚖️👮"},
  custom:{label:"Event",icon:"📌"}
};

function renderCalendar(){
  $("monthTitle").textContent=currentMonth.toLocaleDateString("en-US",{month:"long",year:"numeric"});
  const grid=$("calendarGrid"); grid.innerHTML="";
  const first=new Date(currentMonth);
  const mondayIndex=(first.getDay()+6)%7;
  let cursor=addDays(first,-mondayIndex);
  for(let i=0;i<42;i++){
    const d=new Date(cursor);
    const entry=getEntry(d);
    const cell=document.createElement("button");
    cell.className="day-cell";
    if(d.getMonth()!==currentMonth.getMonth()) cell.classList.add("outside");
    if(dateKey(d)===dateKey(new Date())) cell.classList.add("today");
    if(selectedDate && dateKey(d)===dateKey(selectedDate)) cell.classList.add("selected");
    cell.dataset.date=dateKey(d);
    const m=meta[entry.type]||meta.custom;
    let sub=m.label;
    if(["vacation","sick","comp_taken","extra_ot","extra_comp"].includes(entry.type) && entry.hours) sub += ` • ${entry.hours}h`;
    if(entry.type==="court") sub += ` • ${entry.subpoenas||1} subpoena`;
    cell.innerHTML=`<span class="date-num">${d.getDate()}</span><div class="shift-card type-${entry.type}"><span class="big">${m.icon}</span><span>${sub}</span></div>`;
    cell.addEventListener("click",()=>selectDate(d));
    grid.appendChild(cell);
    cursor=addDays(cursor,1);
  }
  renderSelected();
}

function selectDate(d){ selectedDate=new Date(d); renderCalendar(); }
function renderSelected(){
  if(!selectedDate){ $("editDayBtn").disabled=true; return; }
  const entry=getEntry(selectedDate), m=meta[entry.type]||meta.custom;
  $("selectedDateLabel").textContent=selectedDate.toLocaleDateString("en-US",{weekday:"long",month:"long",day:"numeric",year:"numeric"});
  $("selectedEntryLabel").textContent=`${m.icon} ${m.label}${entry.hours?` — ${entry.hours} hours`:""}${entry.note?` — ${entry.note}`:""}`;
  $("editDayBtn").disabled=false;
}

function openDayDialog(){
  if(!selectedDate)return;
  const key=dateKey(selectedDate), override=state.overrides[key], entry=override||scheduledEntry(selectedDate);
  $("dialogDateTitle").textContent=selectedDate.toLocaleDateString("en-US",{weekday:"long",month:"long",day:"numeric"});
  $("entryType").value=override?.type||"default";
  $("entryHours").value=entry.hours ?? 12;
  $("subpoenaCount").value=entry.subpoenas ?? 1;
  $("entryNote").value=entry.note||"";
  toggleEntryFields();
  $("dayDialog").showModal();
}
function toggleEntryFields(){
  const t=$("entryType").value;
  $("entryHours").parentElement.style.display=["extra_ot","extra_comp","vacation","sick","comp_taken","custom"].includes(t)?"flex":"none";
  $("subpoenaCount").parentElement.style.display=t==="court"?"flex":"none";
}
function saveEntry(){
  const t=$("entryType").value,key=dateKey(selectedDate);
  if(t==="default") delete state.overrides[key];
  else state.overrides[key]={
    type:t,
    hours:Number($("entryHours").value)||(["day","night"].includes(t)?12:0),
    subpoenas:Math.max(0,Math.min(3,Number($("subpoenaCount").value)||0)),
    note:$("entryNote").value.trim()
  };
  saveState(); $("dayDialog").close(); renderAll(); toast("BOLO updated");
}
function deleteEntry(){
  if(!selectedDate)return;
  delete state.overrides[dateKey(selectedDate)];
  saveState(); $("dayDialog").close(); renderAll(); toast("Manual override removed");
}

function periodForDate(date){
  const start=parseDate(state.profile.payPeriodStart);
  if(!start)return null;
  const idx=Math.floor(diffDays(date,start)/14);
  const ps=addDays(start,idx*14);
  return {index:idx,start:ps,end:addDays(ps,13),payday:addDays(parseDate(state.profile.firstPayday),idx*14)};
}

function periodEntries(period){
  const rows=[];
  for(let i=0;i<14;i++){ const d=addDays(period.start,i); rows.push({date:d,entry:getEntry(d)}); }
  return rows;
}

function calculatePeriod(period){
  const rows=periodEntries(period);
  let actualWorked=0, paidLeave=0, court=0, compUsed=0, vacationUsed=0, sickUsed=0;
  const extras=[];
  for(const r of rows){
    const e=r.entry;
    if(["day","night"].includes(e.type)) actualWorked += Number(e.hours||12);
    if(e.type==="extra_ot"||e.type==="extra_comp") extras.push({date:r.date,entry:e,hours:Number(e.hours||12)});
    if(e.type==="vacation"){paidLeave+=Number(e.hours||0);vacationUsed+=Number(e.hours||0);}
    if(e.type==="sick"){paidLeave+=Number(e.hours||0);sickUsed+=Number(e.hours||0);}
    if(e.type==="comp_taken"){paidLeave+=Number(e.hours||0);compUsed+=Number(e.hours||0);}
    if(e.type==="court") court += Math.min(3,Number(e.subpoenas||1))*50;
  }
  extras.sort((a,b)=>a.date-b.date);
  let regularExtra=0, overtimeExtra=0, compStraight=0, compPremium=0;
  for(const x of extras){
    let hours=x.hours;
    const room=Math.max(0,84-actualWorked);
    const straight=Math.min(hours,room);
    const premium=hours-straight;
    actualWorked+=hours;
    if(x.entry.type==="extra_ot"){regularExtra+=straight;overtimeExtra+=premium;}
    else {compStraight+=straight;compPremium+=premium*1.5;}
  }
  const regularWorked=Math.min(actualWorked,84);
  const wage=Number(state.profile.hourlyWage)||0;
  const basePay=regularWorked*wage;
  const overtimePay=overtimeExtra*wage*1.5;
  const leavePay=paidLeave*wage;
  const regularExtraPay=regularExtra*wage;
  const sup=isFirstTwoPaychecksOfMonth(period.payday)?Number(state.profile.supplementalPay)||0:0;
  const total=basePay+overtimePay+leavePay+regularExtraPay+court+sup;
  return {rows,actualWorked,paidLeave,regularWorked,regularExtra,overtimeExtra,compEarned:compStraight+compPremium,
    court,basePay,overtimePay,leavePay,regularExtraPay,supplemental:sup,total,compUsed,vacationUsed,sickUsed};
}

function isFirstTwoPaychecksOfMonth(payday){
  if(!payday)return false;
  const first=parseDate(state.profile.firstPayday);
  if(!first)return false;
  let p=new Date(first), checks=[];
  while(p.getFullYear()<payday.getFullYear() || (p.getFullYear()===payday.getFullYear()&&p.getMonth()<=payday.getMonth())){
    if(p.getFullYear()===payday.getFullYear()&&p.getMonth()===payday.getMonth()) checks.push(dateKey(p));
    p=addDays(p,14);
  }
  return checks.slice(0,2).includes(dateKey(payday));
}

function renderPayroll(){
  const select=$("payPeriodSelect");
  const current=periodForDate(new Date());
  if(!current)return;
  const selectedIndex=Number(select.value||current.index);
  select.innerHTML="";
  for(let i=current.index-6;i<=current.index+12;i++){
    const ps=addDays(parseDate(state.profile.payPeriodStart),i*14), pe=addDays(ps,13), pd=addDays(parseDate(state.profile.firstPayday),i*14);
    const o=document.createElement("option"); o.value=i; o.textContent=`${ps.toLocaleDateString()} – ${pe.toLocaleDateString()} | Pay ${pd.toLocaleDateString()}`;
    if(i===selectedIndex)o.selected=true; select.appendChild(o);
  }
  const idx=Number(select.value);
  const period={index:idx,start:addDays(parseDate(state.profile.payPeriodStart),idx*14),end:addDays(parseDate(state.profile.payPeriodStart),idx*14+13),payday:addDays(parseDate(state.profile.firstPayday),idx*14)};
  const c=calculatePeriod(period);
  $("payrollCards").innerHTML=[
    ["Actual worked",hoursFmt(c.actualWorked),"Only worked hours count toward 84"],
    ["Overtime",hoursFmt(c.overtimeExtra),money(c.overtimePay)],
    ["Comp earned",hoursFmt(c.compEarned),"Straight + qualifying 1.5×"],
    ["Estimated gross",money(c.total),`Payday ${period.payday.toLocaleDateString()}`]
  ].map(x=>`<div class="stat-card"><h4>${x[0]}</h4><strong>${x[1]}</strong><small>${x[2]}</small></div>`).join("");
  $("payrollDetail").innerHTML=`<table class="detail-table">
    <tr><th>Regular worked pay</th><td>${money(c.basePay)}</td></tr>
    <tr><th>Regular-rate extra pay</th><td>${money(c.regularExtraPay)}</td></tr>
    <tr><th>Overtime pay</th><td>${money(c.overtimePay)}</td></tr>
    <tr><th>Paid leave</th><td>${money(c.leavePay)}</td></tr>
    <tr><th>Court pay</th><td>${money(c.court)}</td></tr>
    <tr><th>State supplemental</th><td>${money(c.supplemental)}</td></tr>
    <tr><th>Estimated gross</th><td><strong>${money(c.total)}</strong></td></tr>
  </table>`;
}

function bankBalances(asOf=new Date()){
  let vacation=Number(state.profile.startVacation)||0;
  let sick=Number(state.profile.startSick)||0;
  let comp=Number(state.profile.startComp)||0;
  const start=parseDate(state.profile.patternStart)||new Date();
  for(let y=start.getFullYear();y<=asOf.getFullYear();y++){
    const jan1=new Date(y,0,1);
    if(jan1>=start && jan1<=asOf){
      vacation+=Number(state.profile.annualVacation)||0;
      sick+=Number(state.profile.annualSick)||0;
    }
  }
  Object.entries(state.overrides).forEach(([k,e])=>{
    const d=parseDate(k); if(d>asOf)return;
    if(e.type==="vacation")vacation-=Number(e.hours)||0;
    if(e.type==="sick")sick-=Number(e.hours)||0;
    if(e.type==="comp_taken")comp-=Number(e.hours)||0;
  });
  // comp earned from all periods that intersect known overrides/pattern from start to asOf
  const ppStart=parseDate(state.profile.payPeriodStart);
  if(ppStart){
    let idx=Math.floor(diffDays(start,ppStart)/14);
    let pStart=addDays(ppStart,idx*14);
    while(pStart<=asOf){
      const p={start:pStart,end:addDays(pStart,13),payday:null,index:idx};
      comp+=calculatePeriod(p).compEarned;
      idx++; pStart=addDays(pStart,14);
      if(idx>5000)break;
    }
  }
  for(const a of state.bankAdjustments){
    if(parseDate(a.date)<=asOf){
      if(a.bank==="vacation")vacation+=Number(a.hours)||0;
      if(a.bank==="sick")sick+=Number(a.hours)||0;
      if(a.bank==="comp")comp+=Number(a.hours)||0;
    }
  }
  return {vacation,sick,comp};
}
function renderBanks(){
  const b=bankBalances();
  $("bankCards").innerHTML=[
    ["Vacation Bank",hoursFmt(b.vacation),"🏖️"],["Sick Bank",hoursFmt(b.sick),"🛡️"],["Comp Bank",hoursFmt(b.comp),"⏱️"]
  ].map(x=>`<div class="stat-card"><h4>${x[2]} ${x[0]}</h4><strong>${x[1]}</strong><small>Available balance</small></div>`).join("");
  const items=[...state.bankAdjustments].sort((a,b)=>b.date.localeCompare(a.date)).slice(0,20);
  $("bankHistory").innerHTML=items.length?items.map(a=>`<div class="history-item"><div><strong>${a.bank.toUpperCase()}</strong><small>${a.reason||"Adjustment"} • ${parseDate(a.date).toLocaleDateString()}</small></div><strong>${a.hours>0?"+":""}${a.hours}</strong></div>`).join(""):`<p class="help-text">No manual adjustments yet.</p>`;
}

function loadSettingsForm(){
  const p=state.profile;
  ["profileName","hourlyWage","supplementalPay","patternStart","payPeriodStart","firstPayday","startVacation","annualVacation","startSick","annualSick","startComp"].forEach(id=>$(id).value=p[id]??"");
  $("patternSelect").value=p.pattern;
}
function saveSettings(){
  const map=["profileName","hourlyWage","supplementalPay","patternStart","payPeriodStart","firstPayday","startVacation","annualVacation","startSick","annualSick","startComp"];
  map.forEach(id=>state.profile[id]=$(id).value);
  state.profile.pattern=$("patternSelect").value;
  saveState(); renderAll(); toast("Setup saved");
}

function openPattern(){
  $("quickPattern").value=state.profile.pattern;
  $("quickPatternStart").value=selectedDate?dateKey(selectedDate):state.profile.patternStart;
  $("patternDialog").showModal();
}
function applyPattern(){
  const start=$("quickPatternStart").value;
  state.profile.pattern=$("quickPattern").value;
  state.profile.patternStart=start;
  if($("clearFutureOverrides").checked){
    Object.keys(state.overrides).forEach(k=>{if(k>=start)delete state.overrides[k];});
  }
  saveState(); loadSettingsForm(); $("patternDialog").close(); renderAll(); toast("Pattern applied through 2100");
}

async function shareMonth(){
  const canvas=renderMonthCanvas();
  const fileName=`BOLO-Board-${currentMonth.getFullYear()}-${pad(currentMonth.getMonth()+1)}.png`;
  const text=`${state.profile.name||"BOLO Board"} — ${currentMonth.toLocaleDateString("en-US",{month:"long",year:"numeric"})} schedule`;
  try{
    if(window.Android && Android.shareImage){
      Android.shareImage(canvas.toDataURL("image/png"), fileName, text);
      return;
    }
    const blob=await new Promise(res=>canvas.toBlob(res,"image/png"));
    const file=new File([blob],fileName,{type:"image/png"});
    if(navigator.canShare&&navigator.canShare({files:[file]})) await navigator.share({title:"BOLO Board",text,files:[file]});
    else{
      const a=document.createElement("a");a.href=URL.createObjectURL(blob);a.download=file.name;a.click();URL.revokeObjectURL(a.href);toast("Monthly schedule saved as image");
    }
  }catch(e){ if(e.name!=="AbortError")toast("Share cancelled or unavailable"); }
}
function renderMonthCanvas(){
  const scale=2,w=1080,h=1440,canvas=document.createElement("canvas");canvas.width=w;canvas.height=h;
  const c=canvas.getContext("2d");c.fillStyle="#eef4fb";c.fillRect(0,0,w,h);
  c.fillStyle="#07182c";c.fillRect(0,0,w,180);
  c.fillStyle="#f5b800";c.font="bold 72px sans-serif";c.fillText("★",60,115);
  c.fillStyle="white";c.font="bold 56px sans-serif";c.fillText("BOLO Board",150,85);
  c.font="30px sans-serif";c.fillStyle="#b9d9ff";c.fillText(`${state.profile.name||"Shared Schedule"} • ${currentMonth.toLocaleDateString("en-US",{month:"long",year:"numeric"})}`,150,130);
  const left=30,top=220,cellW=(w-60)/7,cellH=165;
  c.fillStyle="#0d2947";c.fillRect(left,top,w-60,58);
  c.textAlign="center";c.textBaseline="middle";c.font="bold 28px sans-serif";c.fillStyle="white";
  ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"].forEach((x,i)=>c.fillText(x,left+i*cellW+cellW/2,top+29));
  const first=new Date(currentMonth), offset=(first.getDay()+6)%7;let d=addDays(first,-offset);
  for(let i=0;i<42;i++){
    const x=left+(i%7)*cellW,y=top+58+Math.floor(i/7)*cellH,e=getEntry(d),m=meta[e.type]||meta.custom;
    c.fillStyle=d.getMonth()===currentMonth.getMonth()?"#ffffff":"#dde5ef";c.fillRect(x,y,cellW,cellH);
    c.strokeStyle="#9eabbc";c.strokeRect(x,y,cellW,cellH);
    c.textAlign="left";c.textBaseline="top";c.font="bold 24px sans-serif";c.fillStyle="#142033";c.fillText(String(d.getDate()),x+8,y+8);
    c.textAlign="center";c.textBaseline="middle";c.font="42px sans-serif";c.fillText(m.icon,x+cellW/2,y+70);
    c.font="bold 20px sans-serif";c.fillStyle="#142033";c.fillText(m.label,x+cellW/2,y+118);
    if(e.hours){c.font="18px sans-serif";c.fillStyle="#5f6e82";c.fillText(`${e.hours}h`,x+cellW/2,y+145);}
    d=addDays(d,1);
  }
  c.textAlign="left";c.fillStyle="#07182c";c.font="bold 24px sans-serif";c.fillText("Generated by BOLO Board • Financial information hidden",40,1400);
  return canvas;
}

function switchView(name){
  document.querySelectorAll(".view").forEach(v=>v.classList.toggle("active",v.id===`${name}View`));
  document.querySelectorAll(".tab").forEach(t=>t.classList.toggle("active",t.dataset.view===name));
  closeDrawer();
  if(name==="payroll")renderPayroll();
  if(name==="banks")renderBanks();
}
function openDrawer(){$("drawer").classList.add("open");$("drawerBackdrop").classList.add("show")}
function closeDrawer(){$("drawer").classList.remove("open");$("drawerBackdrop").classList.remove("show")}
function renderAll(){renderCalendar();renderPayroll();renderBanks();}

document.addEventListener("DOMContentLoaded",()=>{
  loadSettingsForm(); renderAll();
  $("prevMonth").onclick=()=>{currentMonth.setMonth(currentMonth.getMonth()-1);renderCalendar()};
  $("nextMonth").onclick=()=>{currentMonth.setMonth(currentMonth.getMonth()+1);renderCalendar()};
  $("todayBtn").onclick=()=>{currentMonth=new Date();currentMonth.setDate(1);selectedDate=new Date();renderCalendar()};
  $("shareBtn").onclick=shareMonth;
  $("patternBtn").onclick=openPattern;
  $("editDayBtn").onclick=openDayDialog;
  $("entryType").onchange=toggleEntryFields;
  $("saveEntryBtn").onclick=saveEntry;
  $("deleteEntryBtn").onclick=deleteEntry;
  $("saveSettingsBtn").onclick=saveSettings;
  $("payPeriodSelect").onchange=renderPayroll;
  $("applyPatternBtn").onclick=applyPattern;
  $("adjustBanksBtn").onclick=()=>$("bankDialog").showModal();
  $("saveBankAdjustment").onclick=()=>{
    state.bankAdjustments.push({bank:$("bankType").value,hours:Number($("bankHours").value)||0,reason:$("bankReason").value.trim(),date:dateKey(new Date())});
    saveState();$("bankDialog").close();renderBanks();toast("Bank adjusted");
  };
  $("resetOverridesBtn").onclick=()=>{if(confirm("Clear every manually changed date?")){state.overrides={};saveState();renderAll();toast("Overrides cleared")}};
  $("resetAllBtn").onclick=()=>{if(confirm("Reset the entire BOLO Board app?")){localStorage.removeItem(STORAGE_KEY);location.reload()}};
  $("menuBtn").onclick=openDrawer;$("closeDrawer").onclick=closeDrawer;$("drawerBackdrop").onclick=closeDrawer;
  document.querySelectorAll("[data-view]").forEach(b=>b.onclick=()=>switchView(b.dataset.view));
  $("printMonthBtn").onclick=()=>{switchView("calendar");setTimeout(()=>{if(window.Android&&Android.printPage)Android.printPage();else window.print()},150)};
  $("installBtn").style.display="none";
  window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();deferredPrompt=e});
  // Native Android package: offline files are bundled directly in the APK.
});
