/* =====================================================================
   计算类工具（从"工具 Tab"移到首页入口，作为独立页面）
   ===================================================================== */

/* ---------- 战斗力 ---------- */
function viewBattlePower(){
  const bp = state.data.bp;
  const list = bp.characters;
  const cur = state.bpForm.uid ? list.find(function(c){ return c.uid === state.bpForm.uid; }) : null;
  const r = cur ? calcBattlePower(cur, state.bpForm) : null;

  function numField(key, label){
    return '<div class="field">' +
      '<label>' + label + '</label>' +
      '<input class="input" data-bp="' + key + '" type="number" value="' + state.bpForm[key] + '">' +
    '</div>';
  }

  return '<div class="page">' +
    '<div style="padding:6px 0 10px">' +
      '<button onclick="back()" class="backlink">‹ 返回</button>' +
    '</div>' +
    '<div class="hero" style="padding:2px 0 16px">' +
      '<div class="hero-title" style="font-size:23px">战斗力计算</div>' +
      '<div style="font-size:11.5px;color:var(--ink-3);margin-top:5px">数据版本 v1.1.5 · ' + list.length + ' 位角色</div>' +
    '</div>' +

    '<div class="field">' +
      '<label>选择角色</label>' +
      '<div class="search" style="box-shadow:var(--sh-s);background:var(--surface)">' +
        '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5" stroke-linecap="round"/></svg>' +
        '<input id="bpSearch" placeholder="搜索角色名" value="' + (cur ? esc(cur.name) : '') + '" autocomplete="off">' +
      '</div>' +
      '<div id="bpSuggest"></div>' +
    '</div>' +

    (r ? '<div class="resultbox">' +
      '<div class="resultlabel">最终战斗力</div>' +
      '<div class="resultvalue">' + fmt(r.power) + '</div>' +
      '<div class="resultrows">' +
        '<div class="rr"><span>主攻击（' + cur.attackType + '）</span><b>' + fmt(r.mainAttack) + '</b></div>' +
        '<div class="rr"><span>双防合计</span><b>' + fmt(r.defenseSum) + '</b></div>' +
        '<div class="rr"><span>坨格加成</span><b>' + (r.asideBonus*100).toFixed(1) + '%</b></div>' +
        '<div class="rr"><span>基础分</span><b>' + fmt(r.baseScore) + '</b></div>' +
        '<div class="rr"><span>成长乘区</span><b>×' + r.growth.toFixed(4) + '</b></div>' +
      '</div>' +
    '</div>' : '') +

    '<div class="card" style="padding:16px;margin-bottom:12px">' +
      '<div class="cardtitle">面板属性</div>' +
      numField('attackPhysic', '物理攻击') +
      numField('attackMagic', '魔法攻击') +
      numField('defensePhysic', '物理防御') +
      numField('defenseMagic', '魔法防御') +
      '<div style="margin-bottom:0">' + numField('hp', '生命值') + '</div>' +
    '</div>' +

    '<div class="card" style="padding:16px;margin-bottom:12px">' +
      '<div class="cardtitle">暴击与速度</div>' +
      '<div class="grid2">' +
        ['criticalRate|暴击率','criticalMult|暴击伤害','criticalResist|暴击抗性','criticalMultResist|暴伤抗性']
          .map(function(s){
            const parts = s.split('|');
            return '<div class="field" style="margin-bottom:0"><label>' + parts[1] + '</label>' +
              '<input class="input" data-bp="' + parts[0] + '" type="number" value="' + state.bpForm[parts[0]] + '"></div>';
          }).join('') +
      '</div>' +
      '<div class="field" style="margin-top:13px;margin-bottom:0">' +
        '<label>攻击速度</label>' +
        '<input class="input" data-bp="attackSpeed" type="number" value="' + state.bpForm.attackSpeed + '">' +
      '</div>' +
    '</div>' +

    '<div class="card" style="padding:16px">' +
      '<div class="cardtitle">技能等级</div>' +
      '<div class="grid3">' +
        ['spLevel|低年级','ultLevel|高年级','passiveLevel|被动'].map(function(s){
          const parts = s.split('|');
          return '<div class="field" style="margin-bottom:0"><label>' + parts[1] + '</label>' +
            '<input class="input" data-bp="' + parts[0] + '" type="number" value="' + state.bpForm[parts[0]] + '"></div>';
        }).join('') +
      '</div>' +
      '<div class="field" style="margin-top:13px;margin-bottom:0">' +
        '<label>坨格等级</label>' +
        '<input class="input" data-bp="asideGrade" type="number" value="' + state.bpForm.asideGrade + '">' +
        '<div class="hint">≥ 2 时坨格系数生效（' + (cur ? (cur.asideValueA*100).toFixed(1) + '%' : '—') + '）</div>' +
      '</div>' +
    '</div>' +
    '<div class="formula">公式：基础分 × 成长乘区，TRUNC 取整<br>权重常量来自 TableData.FormalInfo[301–308]</div>' +
  '</div>';
}

/* ---------- 详情（属性格横排） ---------- */
function renderBoard(layer, chId){
  const width = Math.max.apply(null, layer.rows.map(function(r){ return r.length; }).concat([1]));
  let out = '<div class="board" style="--n:' + width + '">';
  for(let ri=0; ri<layer.rows.length; ri++){
    const row = layer.rows[ri];
    out += '<div class="brow">';
    for(let ci=0; ci<width; ci++){
      const cell = layer.cells.find(function(c){ return c.row===ri && c.col===ci; });
      const sym = row[ci] || '.';
      if(!cell || sym === '.'){
        out += '<div class="bcell empty"></div>';
      } else if(cell.attr){
        const lit = state.lit.has(cell.id);
        out += '<div class="bcell attrimg ' + (lit?'lit':'dim') + '" ' +
          'onclick="toggleSlot(\'' + chId + '\',\'' + cell.id + '\')">' +
          '<img src="./assets/cells/' + ATTR_IMG[cell.attr] + '.webp" alt="' + ATTR[cell.attr].name + '">' +
        '</div>';
      } else if(cell.purple){
        out += '<div class="bcell blank purpleattr">' +
          '<img src="./assets/cells/cell_purple_' + cell.purple.toLowerCase() + '.webp" alt="">' +
        '</div>';
      } else if(sym === 'W'){
        out += '<div class="bcell blank"></div>';
      } else if(sym === 'P'){
        out += '<div class="bcell purple"></div>';
      } else if(sym === 'S'){
        out += '<div class="bcell start">起</div>';
      } else if(sym === 'E'){
        out += '<div class="bcell end">终</div>';
      } else {
        out += '<div class="bcell blank"></div>';
      }
    }
    out += '</div>';
  }
  out += '</div>';
  return out;
}

function viewDetail(id){
  const ch = state.data.characters.find(function(c){ return c.id === id; });
  if(!ch) return '<div class="page">未找到该使徒</div>';
  const pr = charProgress(ch);
  const ps = PERSONALITY[ch.personality];
  const layerIdx = Math.min(state.detailTier || 1, 3) - 1;
  const layer = ch.layers[layerIdx];
  const total = layer.slots.length;
  const lit = layer.slots.filter(function(s){ return state.lit.has(s.id); }).length;
  const t = TIER_BY_N[layer.tier];

  return '<div class="page">' +
    '<div style="padding:6px 0 10px">' +
      '<button onclick="back()" class="backlink">‹ 使徒</button>' +
    '</div>' +

    '<div class="detail-hero">' +
      '<img class="detail-av" src="./assets/avatars/' + ch.avatarKey + '.webp" alt="">' +
      '<div style="flex:1">' +
        '<div class="detail-name">' + esc(ch.name) + '</div>' +
        '<div class="detail-tags">' +
          '<span class="tagpill" style="color:' + ps.color + ';background:' + ps.bg + '">' + ps.label + '属性</span>' +
          '<span class="tagpill">' + pr.lit + '/' + pr.total + ' 格</span>' +
          '<span class="tagpill">' + Math.round(pr.pct*100) + '%</span>' +
        '</div>' +
      '</div>' +
    '</div>' +

    '<div class="tierseg" id="tierSeg">' +
      TIERS.map(function(x){
        return '<button class="' + (x.n === layer.tier ? 'on' : '') + '" data-tier="' + x.n + '">' + x.label + '</button>';
      }).join('') +
    '</div>' +

    '<div class="tiercard">' +
      '<div class="tierhead">' +
        '<div class="tiername">' + t.label + '</div>' +
        '<div class="tierprog"><b>' + lit + '</b>/' + total + ' · +' + (lit*t.bonus) + '%</div>' +
      '</div>' +
      renderBoard(layer, ch.id) +
    '</div>' +
  '</div>';
}

window.switchTier = function(n){
  state.detailTier = n;
  render();
};
window.toggleMap = function(){
  state.showMap = !state.showMap;
  render();
};

window.toggleSlot = function(chId, slotId){
  state.lit.has(slotId) ? state.lit.delete(slotId) : state.lit.add(slotId);
  save();
  const v = $('#viewport');
  const top = v.scrollTop;
  render();
  v.scrollTop = top;
};

window.batchPaint = function(chId){
  const ch = state.data.characters.find(function(c){ return c.id === chId; });
  openSheet('<h3 class="sheet-title">批量涂色 · ' + esc(ch.name) + '</h3>' +
    '<p class="sheet-desc">选择要一次操作的层与格子。</p>' +
    '<div class="fgroup"><div class="fglabel">层数</div><div class="fchips">' +
      TIERS.map(function(t){ return '<button class="fchip on" data-btier="' + t.n + '">' + t.label + '</button>'; }).join('') +
    '</div></div>' +
    '<div class="fgroup"><div class="fglabel">格子</div><div class="fchips">' +
      ATTR_ORDER.map(function(a){
        return '<button class="fchip on" data-battr="' + a + '">' +
          '<img class="attrpill-img" src="./assets/cells/' + ATTR_IMG[a] + '.webp" alt="">' + ATTR[a].name + '</button>';
      }).join('') + '</div></div>' +
    '<div class="btnrow" style="margin-top:18px">' +
      '<button class="btn btn-ghost" onclick="doBatch(\'' + chId + '\',false)">全部取消</button>' +
      '<button class="btn btn-primary" onclick="doBatch(\'' + chId + '\',true)">全部点亮</button>' +
    '</div>');
  $$('[data-btier],[data-battr]').forEach(function(b){ b.onclick = function(){ b.classList.toggle('on'); }; });
};

window.doBatch = function(chId, on){
  const ch = state.data.characters.find(function(c){ return c.id === chId; });
  const tiers = new Set($$('[data-btier].on').map(function(b){ return +b.dataset.btier; }));
  const attrs = new Set($$('[data-battr].on').map(function(b){ return b.dataset.battr; }));
  let n = 0;
  ch.layers.forEach(function(l){
    if(!tiers.has(l.tier)) return;
    l.slots.forEach(function(s){
      if(!attrs.has(s.attr)) return;
      if(on){ if(!state.lit.has(s.id)){ state.lit.add(s.id); n++; } }
      else { if(state.lit.has(s.id)){ state.lit.delete(s.id); n++; } }
    });
  });
  save(); closeSheet(); render();
  toast('已' + (on ? '点亮' : '取消') + ' ' + n + ' 格');
};
