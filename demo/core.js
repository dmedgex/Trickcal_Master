/* =====================================================================
   金蜡笔日记 · 新架构 UI 原型 — 核心层
   数据：Trickcal_crayon v1.1.5 真实数据
   算法：PetDispatchSolver / BattlePowerCalculatorEngine 原样移植
   ===================================================================== */
'use strict';

/* ---------------- 常量 ---------------- */
const ATTR = {
  ATTACK:      { name:'攻击力',  short:'攻', color:'#E8735A', sym:'A' },
  CRIT:        { name:'暴击',    short:'暴', color:'#F0A64E', sym:'K' },
  HEALTH:      { name:'生命值',  short:'生', color:'#5EAE72', sym:'H' },
  DEFENSE:     { name:'防御力',  short:'防', color:'#5B85D6', sym:'D' },
  CRIT_RESIST: { name:'暴击抗性',short:'抗', color:'#9B72D4', sym:'R' },
};
const ATTR_ORDER = ['ATTACK','CRIT','HEALTH','DEFENSE','CRIT_RESIST'];
const SYM2ATTR = { A:'ATTACK', K:'CRIT', H:'HEALTH', D:'DEFENSE', R:'CRIT_RESIST' };
const SYM2ATTR_LOWER = { a:'ATTACK', k:'CRIT', h:'HEALTH', d:'DEFENSE', r:'CRIT_RESIST' };

const TIERS = [
  { n:1, key:'FIRST',  label:'第 1 层', bonus:3, cost:2 },
  { n:2, key:'SECOND', label:'第 2 层', bonus:4, cost:4 },
  { n:3, key:'THIRD',  label:'第 3 层', bonus:5, cost:6 },
];
const TIER_BY_N = { 1:TIERS[0], 2:TIERS[1], 3:TIERS[2] };

const PERSONALITY = {
  LIGHT:{ label:'光', color:'#E9B44C', bg:'#FDF4DE' },
  DARK: { label:'暗', color:'#7C6BA8', bg:'#EEEAF7' },
  ICE:  { label:'冰', color:'#5B9BD5', bg:'#E6F0FA' },
  FIRE: { label:'火', color:'#E0673F', bg:'#FBEAE3' },
  GRASS:{ label:'草', color:'#5FA87F', bg:'#E5F3EA' },
};

const RARITY = {
  COMMON:    { label:'普通', color:'#9A9A9A' },
  ADVANCED:  { label:'高级', color:'#5B9BD5' },
  RARE:      { label:'稀有', color:'#9B72D4' },
  LEGENDARY: { label:'传说', color:'#E0B15C' },
};

const TIER_LABEL = {
  SPECIAL:{ label:'特阶', color:'#C89A3F', bg:'#FCF3DF' },
  FIRST:  { label:'一阶', color:'#9B72D4', bg:'#F1EAFB' },
  SECOND: { label:'二阶', color:'#5B85D6', bg:'#E9EFFB' },
  THIRD:  { label:'三阶', color:'#5FA87F', bg:'#E7F4EC' },
  FOURTH: { label:'四阶', color:'#9A9A9A', bg:'#F0F0F0' },
  NONE:   { label:'无',   color:'#B0A69E', bg:'#F5F1ED' },
};

/* ---------------- 状态 ---------------- */
const LS_KEY = 'trickcal-demo-v2';

const state = {
  data: { characters:[], pets:[], regions:[], bp:null },
  allSlots: [],
  lit: new Set(),
  selectedPets: new Set(),
  tab: 'home',
  route: { name:'home', params:{} },
  filters: { q:'', attrs:new Set(), tiers:new Set(), pers:null },
  browseMode: 'DETAIL',
  displayMode: 'DETAIL',
  batchAction: 'UNLOCK',
  detailTier: 1,
  showMap: false,
  saveInfo: { lastBackup: Date.now() - 1000*60*60*26, backups: 3 },
  bpForm: {
    uid: null, attackPhysic: 53080, attackMagic: 4262,
    defensePhysic: 57104, defenseMagic: 57179, hp: 553870,
    criticalRate: 23099, criticalMult: 22942, criticalResist: 21198,
    criticalMultResist: 21207, attackSpeed: 100,
    spLevel: 12, ultLevel: 7, passiveLevel: 7, asideGrade: 2,
  },
  pdForm: { region:null, taskCount:3, overrides:{} },
  pdResult: null,
};

/* ---------------- 工具 ---------------- */
const $  = (s, r=document) => r.querySelector(s);
const $$ = (s, r=document) => [].slice.call(r.querySelectorAll(s));
const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt = n => Math.round(n).toLocaleString('en-US');

function toast(msg){
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('on');
  clearTimeout(t._t);
  t._t = setTimeout(() => t.classList.remove('on'), 1900);
}

function save(){
  try{
    localStorage.setItem(LS_KEY, JSON.stringify({
      lit:[...state.lit], pd:state.pdForm, browseMode:state.browseMode, displayMode:state.displayMode,
    }));
  }catch(e){}
}
function load(){
  try{
    const raw = localStorage.getItem(LS_KEY);
    if(!raw) return false;
    const o = JSON.parse(raw);
    if(o.lit) state.lit = new Set(o.lit);
    if(o.pd) state.pdForm = Object.assign({}, state.pdForm, o.pd);
    if(o.pd && !o.pd.overrides) state.pdForm.overrides = {};
    if(o.browseMode) state.browseMode = o.browseMode;
    if(o.displayMode) state.displayMode = o.displayMode;
    return true;
  }catch(e){ return false; }
}

/* ---------------- 棋盘建模 ---------------- */
function buildSlots(){
  const all = [];
  for(const ch of state.data.characters){
    ch.layers.sort((a,b)=>a.tier-b.tier);
    for(const layer of ch.layers){
      const width = Math.max.apply(null, layer.rows.map(r=>r.length).concat([0]));
      layer.cells = [];
      layer.rows.forEach((row, ri) => {
        for(let ci=0; ci<width; ci++){
          const sym = row[ci] || '.';
          const attr = SYM2ATTR[sym] || null;
          const purple = SYM2ATTR_LOWER[sym] || null;
          /* 稳定 ID：角色 + 层 + 属性 + 坐标（新架构核心改动，不再依赖出现序号） */
          const id = attr ? (ch.id+'_'+layer.tier+'_'+attr.toLowerCase()+'_'+ri+'-'+ci) : null;
          layer.cells.push({ row:ri, col:ci, sym, attr, purple, id, tier: layer.tier, charId: ch.id });
        }
      });
      layer.slots = layer.cells.filter(c => c.attr);
      all.push.apply(all, layer.slots);
    }
  }
  return all;
}

function seedProgress(){
  let seed = 20260911;
  const rnd = () => { seed = (seed*1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
  for(const s of state.allSlots){
    if(rnd() < 0.34) state.lit.add(s.id);
  }
}

function tierStats(ch){
  return ch.layers.map(layer => {
    const total = layer.slots.length;
    const lit = layer.slots.filter(s => state.lit.has(s.id)).length;
    return { tier: layer.tier, total, lit };
  });
}
function charProgress(ch){
  let total=0, lit=0;
  for(const t of tierStats(ch)){ total+=t.total; lit+=t.lit; }
  return { total, lit, pct: total ? lit/total : 0 };
}
function globalStats(){
  const total = state.allSlots.length;
  let lit = 0, bonus = 0, gold = 0;
  const byAttr = {}; ATTR_ORDER.forEach(a => byAttr[a] = { lit:0, bonus:0 });
  for(const s of state.allSlots){
    if(!state.lit.has(s.id)) continue;
    lit++;
    const t = TIER_BY_N[s.tier];
    bonus += t.bonus; gold += t.cost;
    byAttr[s.attr].lit++;
    byAttr[s.attr].bonus += t.bonus;
  }
  return { total, lit, bonus, gold, byAttr, pct: total ? lit/total : 0 };
}

/* =====================================================================
   战斗力引擎（BattlePowerCalculatorEngine 移植）
   ===================================================================== */
const BP_WEIGHTS = {
  attack:2.1, defense:0.7, hp:0.08,
  critRate:0.7, critMult:0.7, critResist:0.7, critMultResist:0.7, atkSpeed:0.6,
};

function calcBattlePower(profile, f){
  const mainAttack = profile.attackType === '物理' ? f.attackPhysic : f.attackMagic;
  const defenseSum = f.defensePhysic + f.defenseMagic;
  const asideBonus = f.asideGrade >= 2 ? profile.asideValueA : 0;
  const baseScore =
    mainAttack * BP_WEIGHTS.attack +
    defenseSum * BP_WEIGHTS.defense +
    f.hp * BP_WEIGHTS.hp +
    f.criticalRate * BP_WEIGHTS.critRate +
    f.criticalMult * BP_WEIGHTS.critMult +
    f.criticalResist * BP_WEIGHTS.critResist +
    f.criticalMultResist * BP_WEIGHTS.critMultResist +
    f.attackSpeed * BP_WEIGHTS.atkSpeed;
  const growth =
    1 +
    profile.activeSkillValueA * f.spLevel +
    profile.ultimateSkillValueA * f.ultLevel +
    profile.passiveValueA * f.passiveLevel +
    profile.weightValueA +
    asideBonus;
  const power = Math.trunc(baseScore * growth);
  return { mainAttack, defenseSum, asideBonus, baseScore, growth, power };
}

/* =====================================================================
   派遣求解器（PetDispatchSolver 移植）
   ===================================================================== */
const PD = { maxBorrowedPerTask:1, maxTotalBorrowed:3 };

function petScoreForTask(pet, task){
  const total = pet.skills.filter(s => task.bonusSkills.indexOf(s.name) >= 0)
                          .reduce((n,s)=>n + s.score, 0);
  return total !== 0 ? total : pet.baseScore;
}

function rewardTierFromScore(score){
  if(score > 37) return 'SPECIAL';
  if(score >= 25) return 'FIRST';
  if(score > 13)  return 'SECOND';
  if(score > 5)   return 'THIRD';
  if(score > 1)   return 'FOURTH';
  return 'NONE';
}
const TIER_RANK = { NONE:0, FOURTH:1, THIRD:2, SECOND:3, FIRST:4, SPECIAL:5 };
const TIER_KEYS = ['special','first','second','third','fourth'];
const TIER_KEY_OF = { SPECIAL:'special', FIRST:'first', SECOND:'second', THIRD:'third', FOURTH:'fourth', NONE:null };

function emptySummary(){ return { special:0, first:0, second:0, third:0, fourth:0 }; }
function withTier(tier){
  const s = emptySummary();
  const k = TIER_KEY_OF[tier];
  if(k) s[k] = 1;
  return s;
}
function addSummary(a,b){
  const o = emptySummary();
  for(let i=0;i<TIER_KEYS.length;i++){ const k = TIER_KEYS[i]; o[k] = a[k] + b[k]; }
  return o;
}
function objTuple(o){
  return [o.tier.special, o.tier.first, o.tier.second, o.tier.third, o.tier.fourth,
          -o.borrowed, -o.pets, o.score];
}
function cmpObj(a,b){
  const A = objTuple(a), B = objTuple(b);
  for(let i=0;i<A.length;i++){ if(A[i] !== B[i]) return A[i] - B[i]; }
  return 0;
}

function combination(list, k){
  const out = [];
  const cur = [];
  (function dfs(start){
    if(cur.length === k){ out.push(cur.slice()); return; }
    const remaining = k - cur.length;
    for(let i=start; i <= list.length - remaining; i++){
      cur.push(list[i]); dfs(i+1); cur.pop();
    }
  })(0);
  return out;
}

function solvePetDispatch(region, taskCount, ownedPets, farmPets){
  if(ownedPets.length === 0) return { ok:false, msg:'请选择至少一只拥有的宠物。' };
  const available = ownedPets.map(p=>({ pet:p, borrowed:false }))
                     .concat(farmPets.map(p=>({ pet:p, borrowed:true })));
  const validTasks = region.tasks.filter(t => t.task && t.task.trim());
  const combos = combination(validTasks, taskCount);
  if(combos.length === 0) return { ok:false, msg:'该区域任务数不足，请减少任务数量。' };

  const scores = new Map();
  for(const c of available){
    if(scores.has(c.pet.id)) continue;
    const m = new Map();
    for(const t of region.tasks) m.set(t.id, petScoreForTask(c.pet, t));
    scores.set(c.pet.id, m);
  }

  const prepared = combos.map(combo => {
    const entries = combo.map((task, idx) => buildTaskEntry(task, idx, available, scores));
    let tier = emptySummary(), minBorrowed = 0, minPets = 0, maxScore = 0, allSpecial = true;
    for(const e of entries){
      tier = addSummary(tier, e.bestTier);
      minBorrowed += e.minBorrowed; minPets += e.minPets; maxScore += e.maxScore;
      if(e.bestRank !== TIER_RANK.SPECIAL) allSpecial = false;
    }
    return { entries, allSpecial, upper:{ tier, borrowed:minBorrowed, pets:minPets, score:maxScore } };
  }).sort((a,b) => (b.allSpecial - a.allSpecial) || cmpObj(b.upper, a.upper));

  let best = null;
  for(const prep of prepared){
    if(best && cmpObj(prep.upper, best.objective) <= 0) continue;
    const allSp = searchAllSpecial(prep.entries);
    if(allSp){ best = allSp; break; }
    const cand = searchBest(prep.entries);
    if(cand && (!best || cmpObj(cand.objective, best.objective) > 0)) best = cand;
  }
  if(!best) return { ok:false, msg:'没有找到有效的派遣方案，请增加宠物或减少任务数。' };
  const out = { ok:true };
  for(const k in best) out[k] = best[k];
  return out;
}

function buildTaskEntry(task, originalIndex, available, scores){
  const bestBySig = new Map();
  function register(team){
    const borrowed = team.filter(p=>p.borrowed).length;
    if(borrowed > PD.maxBorrowedPerTask) return;
    const names = team.map(p=>p.pet.name);
    if(new Set(names).size !== names.length) return;
    const ownedIds = team.filter(p=>!p.borrowed).map(p=>p.pet.id);
    const score = team.reduce((n,p)=> n + scores.get(p.pet.id).get(task.id), 0);
    const tier = rewardTierFromScore(score);
    const cand = { team, ownedIds, borrowed, count:team.length, score, tier };
    const sig = ownedIds.slice().sort((a,b)=>a-b).join(',') + '|' + borrowed + '|' + team.length;
    const old = bestBySig.get(sig);
    if(!old || TIER_RANK[cand.tier] > TIER_RANK[old.tier] ||
       (TIER_RANK[cand.tier] === TIER_RANK[old.tier] && cand.score > old.score)){
      bestBySig.set(sig, cand);
    }
  }
  const n = available.length;
  for(let i=0;i<n;i++){
    register([available[i]]);
    for(let j=i+1;j<n;j++){
      register([available[i], available[j]]);
      for(let k=j+1;k<n;k++){
        register([available[i], available[j], available[k]]);
      }
    }
  }
  let candidates = [];
  bestBySig.forEach(v => candidates.push(v));
  candidates.sort((a,b) =>
    (TIER_RANK[b.tier] - TIER_RANK[a.tier]) ||
    (a.count - b.count) || (a.borrowed - b.borrowed) || (b.score - a.score));
  candidates = candidates.slice(0, 160);

  const specialCandidates = candidates.filter(c => c.tier === 'SPECIAL')
    .sort((a,b) => (a.count-b.count) || (a.borrowed-b.borrowed) || (b.score-a.score));

  const bestRank = candidates.reduce((m,c)=>Math.max(m, TIER_RANK[c.tier]), 0);
  let bestTierKey = 'NONE';
  for(const k in TIER_RANK){ if(TIER_RANK[k] === bestRank){ bestTierKey = k; break; } }

  return {
    task, originalIndex, candidates, specialCandidates,
    bestRank,
    bestTier: withTier(bestTierKey),
    maxScore: candidates.reduce((m,c)=>Math.max(m,c.score),0),
    minPets: candidates.length ? Math.min.apply(null, candidates.map(c=>c.count)) : 0,
    minBorrowed: candidates.length ? Math.min.apply(null, candidates.map(c=>c.borrowed)) : 0,
  };
}

function searchOrder(entries){
  return entries.slice().sort((a,b) =>
    (a.specialCandidates.length - b.specialCandidates.length) ||
    (a.candidates.length - b.candidates.length) ||
    (b.bestRank - a.bestRank) ||
    (b.maxScore - a.maxScore));
}

function searchAllSpecial(entries){
  if(entries.some(e => e.specialCandidates.length === 0)) return null;
  const ordered = searchOrder(entries);
  const chosen = new Array(ordered.length).fill(null);
  const failed = new Set();
  function dfs(i, usedOwned, borrowedTotal){
    if(i === ordered.length) return true;
    const key = i + '|' + [...usedOwned].sort((a,b)=>a-b).join(',') + '|' + borrowedTotal;
    if(failed.has(key)) return false;
    const e = ordered[i];
    for(const c of e.specialCandidates){
      if(borrowedTotal + c.borrowed > PD.maxTotalBorrowed) continue;
      if(c.ownedIds.some(id => usedOwned.has(id))) continue;
      chosen[i] = { entry:e, cand:c };
      const next = new Set(usedOwned); c.ownedIds.forEach(id=>next.add(id));
      if(dfs(i+1, next, borrowedTotal + c.borrowed)) return true;
    }
    failed.add(key);
    return false;
  }
  if(!dfs(0, new Set(), 0)) return null;
  return buildSolution(chosen.filter(Boolean), ordered.length);
}

function searchBest(entries){
  if(entries.some(e => e.candidates.length === 0)) return null;
  const ordered = searchOrder(entries);
  const n = ordered.length;
  const suffixTier = []; for(let i=0;i<=n;i++) suffixTier.push(emptySummary());
  const suffixMinBorrowed = new Array(n+1).fill(0);
  const suffixMinPets = new Array(n+1).fill(0);
  const suffixMaxScore = new Array(n+1).fill(0);
  for(let i=n-1;i>=0;i--){
    suffixTier[i] = addSummary(ordered[i].bestTier, suffixTier[i+1]);
    suffixMinBorrowed[i] = ordered[i].minBorrowed + suffixMinBorrowed[i+1];
    suffixMinPets[i] = ordered[i].minPets + suffixMinPets[i+1];
    suffixMaxScore[i] = ordered[i].maxScore + suffixMaxScore[i+1];
  }
  const chosen = new Array(n).fill(null);
  const cache = new Map();
  let best = null;

  function dfs(i, usedOwned, borrowedTotal, tier, totalPets, totalScore){
    const optimistic = {
      tier: addSummary(tier, suffixTier[i]),
      borrowed: borrowedTotal + suffixMinBorrowed[i],
      pets: totalPets + suffixMinPets[i],
      score: totalScore + suffixMaxScore[i],
    };
    if(best && cmpObj(optimistic, best.objective) <= 0) return;

    const key = i + '|' + [...usedOwned].sort((a,b)=>a-b).join(',') + '|' + borrowedTotal;
    const prefixObj = { tier, borrowed:0, pets:totalPets, score:totalScore };
    const cached = cache.get(key);
    if(cached && cmpObj(prefixObj, cached) <= 0) return;
    cache.set(key, prefixObj);

    if(i === n){
      const sol = buildSolution(chosen.filter(Boolean), n);
      if(!best || cmpObj(sol.objective, best.objective) > 0) best = sol;
      return;
    }
    const e = ordered[i];
    for(const c of e.candidates){
      const nb = borrowedTotal + c.borrowed;
      if(nb > PD.maxTotalBorrowed) continue;
      if(c.ownedIds.some(id => usedOwned.has(id))) continue;
      chosen[i] = { entry:e, cand:c };
      const next = new Set(usedOwned); c.ownedIds.forEach(id=>next.add(id));
      dfs(i+1, next, nb, addSummary(tier, withTier(c.tier)),
          totalPets + c.count, totalScore + c.score);
    }
  }
  dfs(0, new Set(), 0, emptySummary(), 0, 0);
  return best;
}

function buildSolution(pairs, taskCount){
  const assignments = new Array(taskCount).fill(null);
  let totalScore=0, borrowed=0, totalPets=0, tier=emptySummary();
  for(const pair of pairs){
    const entry = pair.entry, cand = pair.cand;
    assignments[entry.originalIndex] = {
      task: entry.task,
      team: cand.team.map(p=>Object.assign({}, p.pet, { isBorrowed:p.borrowed })),
      score: cand.score,
      tier: cand.tier,
    };
    totalScore += cand.score;
    borrowed += cand.borrowed;
    totalPets += cand.count;
    tier = addSummary(tier, withTier(cand.tier));
  }
  return {
    assignments: assignments.filter(Boolean),
    totalScore, borrowed, totalPets, tier,
    allSpecial: tier.special === taskCount,
    objective: { tier, borrowed, totalPets, score: totalScore },
  };
}
