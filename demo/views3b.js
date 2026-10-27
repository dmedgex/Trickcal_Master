/* ---------- 宠物派遣（含用户自定义任务特性） ---------- */
const PET_SKILLS = ['迟钝','活泼','亲密','敏锐','体贴','自信'];

/* 任务键必须含区域，否则不同区域同 id 的任务会互相覆盖（旧版 bug） */
function taskKey(regionName, taskId){ return regionName + '#' + taskId; }
function skillOverride(regionName, taskId){
  return state.pdForm.overrides[taskKey(regionName, taskId)];
}
/* 生效特性：用户覆盖优先，否则用下发值 */
function effectiveBonus(regionName, task){
  if(!task) return [];
  return skillOverride(regionName, task.id) || task.bonusSkills || [];
}
function regionWithOverrides(region){
  return Object.assign({}, region, {
    tasks: region.tasks.map(function(t){
      return Object.assign({}, t, { bonusSkills: effectiveBonus(region.name, t) });
    }),
  });
}

function viewPetDispatch(){
  const d = state.data;
  const region = d.regions.find(function(r){ return r.name === state.pdForm.region; }) || d.regions[0];
  if(region && state.pdForm.region !== region.name) state.pdForm.region = region.name;

  const owned = d.pets.filter(function(pet){ return state.selectedPets.has(pet.id); });
  const result = state.pdResult;
  const overrideCount = region.tasks.filter(function(t){
    return !!skillOverride(region.name, t.id);
  }).length;

  return '<div class="page">' +
    '<div style="padding:6px 0 10px">' +
      '<button onclick="back()" class="backlink">‹ 工具</button>' +
    '</div>' +
    '<div class="hero" style="padding:2px 0 16px">' +
      '<div class="hero-title" style="font-size:23px">宠物派遣</div>' +
      '<div style="font-size:11.5px;color:var(--ink-3);margin-top:5px">' +
        d.pets.length + ' 只宠物 · ' + d.regions.length + ' 个区域 · ' +
        d.regions.reduce(function(n,r){ return n + r.tasks.length; }, 0) + ' 个任务</div>' +
    '</div>' +

    '<div class="card" style="padding:16px;margin-bottom:12px">' +
      '<div class="field" style="margin-bottom:13px">' +
        '<label>派遣区域</label>' +
        '<select class="input" id="pdRegion">' +
          d.regions.map(function(r){
            return '<option ' + (r.name === region.name ? 'selected' : '') + '>' + esc(r.name) + '</option>';
          }).join('') +
        '</select>' +
      '</div>' +
      '<div class="field" style="margin-bottom:0">' +
        '<label>执行任务数</label>' +
        '<div class="seg" id="pdCount">' +
          [1,2,3,4,5].map(function(n){
            return '<button data-n="' + n + '" class="' + (state.pdForm.taskCount===n?'on':'') + '">' + n + '</button>';
          }).join('') +
        '</div>' +
      '</div>' +
    '</div>' +

    '<div class="card" style="padding:16px;margin-bottom:12px">' +
      '<div class="rowhead">' +
        '<div class="cardtitle" style="margin:0">任务加成特性</div>' +
        '<span class="hintpill">' + (overrideCount ? '已自定义 ' + overrideCount + ' 个' : '全部使用默认') + '</span>' +
      '</div>' +
      '<p class="tip">每个任务实际刷出的特性以你自己的游戏内为准，在这里改完再计算。</p>' +
      region.tasks.map(function(t){
        const cur = effectiveBonus(region.name, t);
        const custom = !!skillOverride(region.name, t.id);
        return '<div class="taskrow ' + (custom?'custom':'') + '">' +
          '<div class="taskrow-main">' +
            '<div class="taskrow-name">' + esc(t.task) + '</div>' +
            '<div class="taskrow-area">' + esc(t.area) + '</div>' +
          '</div>' +
          '<div class="taskrow-skills">' +
            (cur.length
              ? cur.map(function(s){ return '<span class="skilltag">' + esc(s) + '</span>'; }).join('')
              : '<span class="skilltag none">无</span>') +
            '</div>' +
          '<button class="editbtn" onclick="editTaskSkills(' + t.id + ')">改</button>' +
        '</div>';
      }).join('') +
      (overrideCount ? '<button class="btn btn-ghost btn-block" style="margin-top:10px" onclick="resetTaskSkills()">恢复全部默认</button>' : '') +
    '</div>' +

    '<div class="card" style="padding:16px;margin-bottom:12px">' +
      '<div class="rowhead">' +
        '<div class="cardtitle" style="margin:0">选择拥有宠物</div>' +
        '<div style="font-size:11px;color:var(--ink-3)">已选 <b id="pdOwnedCount" style="color:var(--brand)">' + owned.length + '</b></div>' +
      '</div>' +
      '<div class="petgrid">' +
        d.pets.map(function(pet){
          const on = state.selectedPets.has(pet.id);
          const rr = RARITY[pet.rarity];
          return '<button class="petcell ' + (on?'on':'') + '" data-pid="' + pet.id + '" title="' + esc(pet.name) + '">' +
            '<img src="./assets/pets/' + pet.id + '.webp" loading="lazy" alt="' + esc(pet.name) + '">' +
            '<div class="rarity-bar" style="background:' + rr.color + '"></div>' +
          '</button>';
        }).join('') +
      '</div>' +
      '<div style="display:flex;gap:8px">' +
        '<button class="btn btn-ghost" onclick="selectAllPets(true)">全选</button>' +
        '<button class="btn btn-ghost" onclick="selectAllPets(false)">清空</button>' +
      '</div>' +
    '</div>' +

    '<button class="btn btn-primary btn-block calcbtn" onclick="runPetDispatch()">计算最优方案</button>' +

    (result ? renderPdResult(result, region) : '') +
  '</div>';
}

window.editTaskSkills = function(taskId){
  const region = state.data.regions.find(function(r){ return r.name === state.pdForm.region; });
  const task = region.tasks.find(function(t){ return t.id === taskId; });
  const cur = effectiveBonus(region.name, task);

  openSheet('<h3 class="sheet-title">修改加成特性</h3>' +
    '<p class="sheet-desc">' + esc(task.task) + ' · ' + esc(task.area) + '<br>每只宠物最多 2 个特性，选择实际刷到的。</p>' +
    '<div class="fgroup"><div class="fchips">' +
      PET_SKILLS.map(function(s){
        return '<button class="fchip ' + (cur.indexOf(s)>=0?'on':'') + '" data-ps="' + s + '">' + s + '</button>';
      }).join('') + '</div>' +
      '<div class="sheet-note">最多选 2 个，也可一个都不选（视为无加成）</div>' +
    '</div>' +
    '<div class="btnrow" style="margin-top:16px">' +
      '<button class="btn btn-ghost" onclick="clearTaskSkills(' + taskId + ')">用默认值</button>' +
      '<button class="btn btn-primary" onclick="saveTaskSkills(' + taskId + ')">保存</button>' +
    '</div>');

  $$('[data-ps]').forEach(function(b){
    b.onclick = function(){
      const sel = $$('[data-ps].on');
      if(!b.classList.contains('on') && sel.length >= 2){ toast('最多选择 2 个特性'); return; }
      b.classList.toggle('on');
    };
  });
};

window.saveTaskSkills = function(taskId){
  const region = state.data.regions.find(function(r){ return r.name === state.pdForm.region; });
  const picked = $$('[data-ps].on').map(function(b){ return b.dataset.ps; });
  const key = taskKey(region.name, taskId);
  if(picked.length === 0) delete state.pdForm.overrides[key];
  else state.pdForm.overrides[key] = picked;
  save(); closeSheet(); state.pdResult = null; render();
  toast(picked.length ? '已设为 ' + picked.join(' / ') : '已恢复默认特性');
};

window.clearTaskSkills = function(taskId){
  const region = state.data.regions.find(function(r){ return r.name === state.pdForm.region; });
  delete state.pdForm.overrides[taskKey(region.name, taskId)];
  save(); closeSheet(); state.pdResult = null; render();
  toast('已恢复默认特性');
};

window.resetTaskSkills = function(){
  const region = state.data.regions.find(function(r){ return r.name === state.pdForm.region; });
  region.tasks.forEach(function(t){ delete state.pdForm.overrides[taskKey(region.name, t.id)]; });
  save(); closeSheet(); state.pdResult = null; render();
  toast('已恢复本区域全部默认特性');
};

function renderPdResult(res, region){
  if(!res.ok){
    return '<div class="card" style="padding:16px;color:var(--warn);font-size:12.5px">' + esc(res.msg) + '</div>';
  }
  return '<div class="sec" style="margin-top:0">' +
    '<div class="sec-head"><div class="sec-title">最优方案</div>' +
      '<span style="font-size:11px;color:var(--ink-3)">' + esc(region.name) + '</span></div>' +
    '<div class="resultbox" style="margin-bottom:14px">' +
      '<div style="display:flex;justify-content:space-between;align-items:flex-end">' +
        '<div><div class="resultlabel">总得分</div>' +
          '<div class="resultvalue">' + res.totalScore + '</div></div>' +
        '<div style="text-align:right;font-size:11px;color:var(--ink-2);line-height:1.7">' +
          '借用 <b>' + res.borrowed + '</b> 只<br>使用 <b>' + res.totalPets + '</b> 只</div>' +
      '</div>' +
      '<div style="display:flex;gap:6px;margin-top:13px;flex-wrap:wrap">' +
        (TIER_KEYS.filter(function(k){ return res.tier[k] > 0; }).map(function(k){
          const t = TIER_LABEL[k.toUpperCase()];
          return '<span class="chip" style="background:' + t.bg + ';color:' + t.color + ';box-shadow:none;font-weight:750">' +
            t.label + ' × ' + res.tier[k] + '</span>';
        }).join('') || '<span class="chip">无奖励</span>') +
        (res.allSpecial ? '<span class="chip" style="background:#FCF3DF;color:#C89A3F;box-shadow:none;font-weight:750">✦ 全特阶</span>' : '') +
      '</div>' +
    '</div>' +
    res.assignments.map(function(a, i){
      const t = TIER_LABEL[a.tier];
      return '<div class="assign">' +
        '<div class="assign-top">' +
          '<div>' +
            '<div class="assign-name">' + (i+1) + '. ' + esc(a.task.task) + '</div>' +
            '<div class="assign-area">' + esc(a.task.area) + ' · ' +
              (a.task.bonusSkills.length ? a.task.bonusSkills.join(' / ') : '无加成特性') + '</div>' +
          '</div>' +
          '<span class="tierbadge" style="background:' + t.bg + ';color:' + t.color + '">' + t.label + '</span>' +
        '</div>' +
        '<div class="assign-team">' +
          a.team.map(function(pet){
            return '<div class="tm"><img src="./assets/pets/' + pet.id + '.webp" alt="">' +
              esc(pet.name) + (pet.isBorrowed ? '<span class="brw">借</span>' : '') + '</div>';
          }).join('') +
          '<div class="assign-score">' + a.score + ' 分</div>' +
        '</div>' +
      '</div>';
    }).join('') +
  '</div>';
}

window.selectAllPets = function(on){
  if(on) state.data.pets.forEach(function(pet){ state.selectedPets.add(pet.id); });
  else state.selectedPets.clear();
  save(); render();
};

window.runPetDispatch = function(){
  const d = state.data;
  const baseRegion = d.regions.find(function(r){ return r.name === state.pdForm.region; });
  const region = regionWithOverrides(baseRegion);
  const owned = d.pets.filter(function(pet){ return state.selectedPets.has(pet.id); });
  if(owned.length === 0){ toast('请先选择宠物'); return; }
  const t0 = performance.now();
  const res = solvePetDispatch(region, state.pdForm.taskCount, owned, []);
  res.ms = performance.now() - t0;
  state.pdResult = res;
  render();
  if(res.ok) toast('求解完成 · ' + res.ms.toFixed(0) + 'ms');
};
