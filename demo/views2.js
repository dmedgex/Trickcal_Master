/* ---------- 使徒列表（严格对齐旧版 CharacterListScreen.kt） ---------- */
/* 旧版 CharacterFilterMatcher.matchesAnyTierAnyAttribute：
   1) 先在选中层里取候选层（未选层则全部层）
   2) 未选属性 → 候选层里有格子即可
   3) 选了属性 → 候选层中必须存在「某一层同时包含全部选中属性」
   层与属性不是各自独立判断，而是同层交集 */
function filteredCharacters(){
  const f = state.filters;
  const q = f.q.trim().toLowerCase();
  const attrs = Array.from(f.attrs);
  return state.data.characters.filter(function(ch){
    if(q && ch.name.toLowerCase().indexOf(q) < 0 && ch.id.toLowerCase().indexOf(q) < 0) return false;
    if(f.pers && ch.personality !== f.pers) return false;

    const candidates = f.tiers.size
      ? ch.layers.filter(function(l){ return f.tiers.has(l.tier); })
      : ch.layers;
    if(!candidates.length) return false;
    if(!attrs.length){
      return candidates.some(function(l){ return l.slots.length > 0; });
    }
    return candidates.some(function(l){
      return attrs.every(function(a){
        return l.slots.some(function(s){ return s.attr === a; });
      });
    });
  });
}

const ATTR_IMG = {
  ATTACK:'cell_attack', CRIT:'cell_crit', HEALTH:'cell_health',
  DEFENSE:'cell_defense', CRIT_RESIST:'cell_crit_resist',
};
const PERS_IMG = {
  LIGHT:'light', DARK:'dark', ICE:'ice', FIRE:'fire', GRASS:'grass',
};

/* 旧版 resolveQuickPaintTarget：
   仅在「层数单选 + 属性单选」时成立，取该层该属性的全部格子；
   shouldUnlock = 这组格子一个都没涂过 */
function quickPaintTarget(ch){
  const f = state.filters;
  if(f.tiers.size !== 1 || f.attrs.size !== 1) return null;
  const tier = f.tiers.values().next().value;
  const attr = f.attrs.values().next().value;
  const layer = ch.layers.find(function(l){ return l.tier === tier; });
  if(!layer) return null;
  const ids = layer.slots
    .filter(function(s){ return s.attr === attr; })
    .map(function(s){ return s.id; });
  if(!ids.length) return null;
  return {
    ids: ids,
    shouldUnlock: ids.every(function(id){ return !state.lit.has(id); })
  };
}

/* 旧版 shouldDimCompactCard：存在可涂目标且整组未涂 → 加暗罩（灰），
   一旦涂过就摘掉（亮起来） */
function isDimmed(ch){
  const t = quickPaintTarget(ch);
  return !!(t && t.shouldUnlock);
}

/* 旧版 quickPaintCharacter：整组一起涂亮，或已有涂色时整组取消 */
function quickPaintCommit(ch){
  const t = quickPaintTarget(ch);
  if(!t) return;
  t.ids.forEach(function(id){
    if(t.shouldUnlock) state.lit.add(id); else state.lit.delete(id);
  });
  save();
  const v = $('#viewport');
  const top = v.scrollTop;
  render();
  v.scrollTop = top;
  toast((t.shouldUnlock ? '已点亮 ' : '已取消 ') + t.ids.length + ' 格');
}

/* 旧版 onCharacterClick 的三分支路由 */
function characterClick(ch){
  const isPaint = state.browseMode === 'PAINT';
  if(state.displayMode === 'COMPACT' && isPaint && quickPaintTarget(ch)){
    quickPaintCommit(ch);
    return;
  }
  if(!isPaint){ nav('detail', { id: ch.id }); return; }
  quickPaint(ch.id);
}

/* 旧版 CharacterCard：
   Card(padding 12) → Row(头像44 + 名称/进度 + 图标) → Divider → 每层[标题 labelLarge + FlowRow(22dp, 4dp)] */
function characterCard(ch){
  const lit = ch.layers.reduce(function(n,l){
    return n + l.slots.filter(function(s){ return state.lit.has(s.id); }).length;
  }, 0);
  const total = ch.layers.reduce(function(n,l){ return n + l.slots.length; }, 0);
  const ps = PERSONALITY[ch.personality];
  const dim = isDimmed(ch);
  const paintMode = state.browseMode === 'PAINT';

  return '<div class="ccard" style="--pers:' + ps.color + ';--persbg:' + ps.bg + '" ' +
      'onclick="nav(\'detail\',{id:\'' + ch.id + '\'})">' +
    '<div class="ccard-top">' +
      '<img class="ccard-av' + (dim ? ' dim' : '') + (paintMode ? ' paintable' : '') + '" ' +
        'src="./assets/avatars/' + ch.avatarKey + '.webp" loading="lazy" alt="" ' +
        (paintMode ? 'title="点击快捷涂色" onclick="event.stopPropagation();avatarPaint(\'' + ch.id + '\')"' : '') + '>' +
      '<div class="ccard-mid">' +
        '<div class="ccard-name">' + esc(ch.name) + '</div>' +
        '<div class="ccard-prog">进度 ' + lit + '/' + total + '</div>' +
      '</div>' +
      '<div class="ccard-go">' + (state.browseMode === 'PAINT' ? '✎' : '›') + '</div>' +
    '</div>' +
    '<div class="ccard-line"></div>' +
    '<div class="ccard-layers">' +
      ch.layers.map(function(l){
        return '<div class="clayer">' +
          '<div class="clayer-t">第' + l.tier + '层</div>' +
          '<div class="cgrid">' +
            l.slots.map(function(s){
              return '<img class="cicon ' + (state.lit.has(s.id)?'lit':'dim') + '" ' +
                'src="./assets/cells/' + ATTR_IMG[s.attr] + '.webp" ' +
                'onclick="event.stopPropagation();toggleSlot(\'' + ch.id + '\',\'' + s.id + '\')" ' +
                'alt="' + ATTR[s.attr].name + '">';
            }).join('') +
          '</div>' +
        '</div>';
      }).join('') +
    '</div>' +
  '</div>';
}

function viewApostle(){
  const list = filteredCharacters();
  const g = globalStats();
  const fcount = state.filters.attrs.size + state.filters.tiers.size + (state.filters.pers ? 1 : 0);
  const isPaint = state.browseMode === 'PAINT';

  return '<div class="page">' +
    '<div class="listhead">' +
      '<div class="searchbar">' +
        '<div class="search">' +
          '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5" stroke-linecap="round"/></svg>' +
          '<input id="q" placeholder="搜索使徒" value="' + esc(state.filters.q) + '">' +
        '</div>' +
        '<button class="iconbtn" title="展示密度" onclick="toggleDisplayMode()">' +
          (state.displayMode === 'DETAIL' ? '▦' : '▤') + '</button>' +
        '<button class="iconbtn" title="一键涂色" onclick="openBatchPaint()">✎</button>' +
      '</div>' +
      '<div class="modebar">' +
        '<div class="seg seg-sm">' +
          '<button class="' + (!isPaint?'on':'') + '" onclick="setBrowseMode(\'DETAIL\')">查看详情</button>' +
          '<button class="' + (isPaint?'on':'') + '" onclick="setBrowseMode(\'PAINT\')">蜡笔涂色</button>' +
        '</div>' +
      '</div>' +
      filterRows(fcount) +
    '</div>' +

    '<div class="listmeta">' +
      '<span>' + list.length + ' 位使徒' +
        (list.length !== state.data.characters.length ? ' / 共 ' + state.data.characters.length : '') + '</span>' +
      '<span>总进度 <b>' + g.lit + '</b>/' + g.total + '</span>' +
    '</div>' +

    (state.displayMode === 'DETAIL'
      ? '<div class="charlist">' + (list.map(characterCard).join('') || emptyHint()) + '</div>'
      : '<div class="chargrid">' + (list.map(function(ch){
          const p2 = charProgress(ch);
          const ps = PERSONALITY[ch.personality];
          return '<div class="cgitem' + (isDimmed(ch) ? ' dimmed' : '') + '" style="--pers:' + ps.color + ';--persbg:' + ps.bg + '" onclick="characterClickById(\'' + ch.id + '\')">' +
            '<img src="./assets/avatars/' + ch.avatarKey + '.webp" loading="lazy" alt="">' +
            (p2.pct === 1 ? '<div class="cgfull">✦</div>' : '') +
            '<div class="cgname">' + esc(ch.name) + '</div>' +
          '</div>';
        }).join('') || emptyHint()) + '</div>') +
  '</div>';
}

/* 三行筛选 —— 完全照抄旧版两个 Strip 的算法：
     cellWidth = (可用宽 - spacing*(n-1)) / n
     cell 是正方形，图标取 cell 的 90%
     选中/未选中只有 alpha 1.0 / 0.4，没有背景、没有阴影、没有边框 */
function filterRows(fcount){
  const f = state.filters;
  return '<div class="filterrows">' +
    '<div class="tierrow">' +
      TIERS.map(function(t){
        return '<button class="tierpill ' + (f.tiers.has(t.n)?'on':'') + '" onclick="toggleTier(' + t.n + ')">' + t.label + '</button>';
      }).join('') +
    '</div>' +
    '<div class="iconrow">' +
      ATTR_ORDER.map(function(a){
        return '<button class="bstrip ' + (f.attrs.has(a)?'on':'') + '" onclick="toggleAttr(\'' + a + '\')" title="' + ATTR[a].name + '">' +
          '<img src="./assets/cells/' + ATTR_IMG[a] + '.webp" alt="' + ATTR[a].name + '">' +
        '</button>';
      }).join('') +
    '</div>' +
    '<div class="iconrow">' +
      Object.keys(PERSONALITY).map(function(k){
        return '<button class="bstrip ' + (f.pers===k?'on':'') + '" onclick="togglePers(\'' + k + '\')" title="' + PERSONALITY[k].label + '">' +
          '<img src="./assets/pers/' + PERS_IMG[k] + '.webp" alt="' + PERSONALITY[k].label + '">' +
        '</button>';
      }).join('') +
    '</div>' +
    (fcount ? '<button class="clearlink" onclick="clearFilter()">清空筛选</button>' : '') +
  '</div>';
}

function emptyHint(){
  return '<div class="empty">没有匹配的使徒</div>';
}

window.toggleAttr = function(a){
  const s = state.filters.attrs;
  s.has(a) ? s.delete(a) : s.add(a);
  render();
};
window.clearFilter = function(){
  state.filters = { q:'', attrs:new Set(), tiers:new Set(), pers:null };
  render();
};
window.setBrowseMode = function(m){ state.browseMode = m; render(); };
window.toggleDisplayMode = function(){
  state.displayMode = state.displayMode === 'DETAIL' ? 'COMPACT' : 'DETAIL';
  render();
};
window.toggleTier = function(n){
  const s = state.filters.tiers;
  s.has(n) ? s.delete(n) : s.add(n);
  render();
};
window.togglePers = function(k){
  state.filters.pers = state.filters.pers === k ? null : k;
  render();
};

window.characterClickById = function(chId){
  const ch = state.data.characters.find(function(c){ return c.id === chId; });
  if(ch) characterClick(ch);
};

window.avatarPaint = function(chId){
  const ch = state.data.characters.find(function(c){ return c.id === chId; });
  if(!ch) return;
  if(quickPaintTarget(ch)){ quickPaintCommit(ch); return; }
  quickPaint(chId);
};

window.quickPaint = function(chId){
  const ch = state.data.characters.find(function(c){ return c.id === chId; });
  openSheet('<h3 class="sheet-title">' + esc(ch.name) + '</h3>' +
    '<p class="sheet-desc">选择要涂的层与格子。</p>' +
    '<div class="fgroup"><div class="fglabel">层数</div><div class="fchips">' +
      ch.layers.map(function(l){
        return '<button class="fchip" data-qtier="' + l.tier + '">第' + l.tier + '层</button>';
      }).join('') + '</div></div>' +
    '<div class="fgroup"><div class="fglabel">格子</div><div class="fchips">' +
      ATTR_ORDER.map(function(a){
        return '<button class="fchip" data-qattr="' + a + '">' +
          '<img class="attrpill-img" src="./assets/cells/' + ATTR_IMG[a] + '.webp" alt="">' + ATTR[a].name + '</button>';
      }).join('') + '</div></div>' +
    '<div class="btnrow" style="margin-top:16px">' +
      '<button class="btn btn-ghost" onclick="closeSheet()">取消</button>' +
      '<button class="btn btn-primary" onclick="applyQuickPaint(\'' + chId + '\')">涂色</button>' +
    '</div>');
  $$('[data-qtier],[data-qattr]').forEach(function(b){ b.onclick = function(){ b.classList.toggle('on'); }; });
};

window.applyQuickPaint = function(chId){
  const ch = state.data.characters.find(function(c){ return c.id === chId; });
  const tiers = new Set($$('[data-qtier].on').map(function(b){ return +b.dataset.qtier; }));
  const attrs = new Set($$('[data-qattr].on').map(function(b){ return b.dataset.qattr; }));
  if(!tiers.size || !attrs.size){ toast('请选择层数与格子'); return; }
  let n = 0;
  ch.layers.forEach(function(l){
    if(!tiers.has(l.tier)) return;
    l.slots.forEach(function(s){
      if(!attrs.has(s.attr)) return;
      if(!state.lit.has(s.id)){ state.lit.add(s.id); n++; }
    });
  });
  save(); closeSheet(); render();
  toast('已点亮 ' + n + ' 格');
};

/* ---------- 一键涂色（旧版 BatchPaintPlanner：先预览再执行） ---------- */
function batchPreview(){
  const tiers = state.filters.tiers;
  const attrs = state.filters.attrs;
  const on = state.batchAction !== 'LOCK';
  if(!tiers.size || !attrs.size){
    return { ok:false, msg:'请先在上方选择「层数」和「格子」。', chars:0, slots:0, ids:[] };
  }
  const chs = filteredCharacters();
  const ids = [];
  const hitChars = new Set();
  chs.forEach(function(ch){
    ch.layers.forEach(function(l){
      if(!tiers.has(l.tier)) return;
      l.slots.forEach(function(s){
        if(!attrs.has(s.attr)) return;
        const isLit = state.lit.has(s.id);
        if(on ? !isLit : isLit){ ids.push(s.id); hitChars.add(ch.id); }
      });
    });
  });
  return { ok: ids.length > 0, msg: ids.length ? '' : '当前条件下没有可操作的格子。',
           chars: hitChars.size, slots: ids.length, ids: ids };
}

window.openBatchPaint = function(){
  const pv = batchPreview();
  openSheet('<h3 class="sheet-title">一键涂色</h3>' +
    '<p class="sheet-desc">对当前筛选结果批量操作。</p>' +
    '<div class="fgroup"><div class="fglabel">操作</div><div class="fchips">' +
      '<button class="fchip ' + (state.batchAction!=='LOCK'?'on':'') + '" data-act="UNLOCK">批量点亮</button>' +
      '<button class="fchip ' + (state.batchAction==='LOCK'?'on':'') + '" data-act="LOCK">批量取消</button>' +
    '</div></div>' +
    '<div class="previewbox ' + (pv.ok?'':'muted') + '">' +
      (pv.ok ? '将影响 <b>' + pv.chars + '</b> 位使徒，共 <b>' + pv.slots + '</b> 个格子' : esc(pv.msg)) +
    '</div>' +
    '<div class="btnrow" style="margin-top:16px">' +
      '<button class="btn btn-ghost" onclick="closeSheet()">取消</button>' +
      '<button class="btn btn-primary" style="' + (pv.ok?'':'opacity:.45;pointer-events:none') + '" ' +
        'onclick="applyBatchPaint()">确认' + (state.batchAction==='LOCK'?'取消':'点亮') + '</button>' +
    '</div>');
  $$('[data-act]').forEach(function(b){
    b.onclick = function(){ state.batchAction = b.dataset.act; closeSheet(); openBatchPaint(); };
  });
};

window.applyBatchPaint = function(){
  const pv = batchPreview();
  if(!pv.ok){ toast(pv.msg || '没有可操作的格子'); return; }
  const on = state.batchAction !== 'LOCK';
  pv.ids.forEach(function(id){ on ? state.lit.add(id) : state.lit.delete(id); });
  save(); closeSheet(); render();
  toast('已' + (on ? '点亮' : '取消') + ' ' + pv.ids.length + ' 格');
};
