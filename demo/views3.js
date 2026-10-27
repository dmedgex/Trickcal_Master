/* =====================================================================
   统计 Tab
   ===================================================================== */
const RING_R = 37;
const RING_C = 2 * Math.PI * RING_R;

function fullBonusTotal(){
  let v = 0;
  for(const s of state.allSlots) v += TIER_BY_N[s.tier].bonus;
  return v;
}

function statsSnapshot(){
  const byAttr = {}, byTier = {}, byTierAttr = {};
  ATTR_ORDER.forEach(function(a){
    byAttr[a] = { lit:0, total:0, bonus:0, gold:0 };
    byTierAttr[a] = {};
    TIERS.forEach(function(t){ byTierAttr[a][t.n] = { lit:0, total:0, bonus:0, gold:0 }; });
  });
  TIERS.forEach(function(t){ byTier[t.n] = { lit:0, total:0, bonus:0, gold:0 }; });

  for(const s of state.allSlots){
    const t = TIER_BY_N[s.tier];
    const isLit = state.lit.has(s.id);
    byAttr[s.attr].total++; byTier[s.tier].total++; byTierAttr[s.attr][s.tier].total++;
    if(isLit){
      byAttr[s.attr].lit++; byAttr[s.attr].bonus += t.bonus; byAttr[s.attr].gold += t.cost;
      byTier[s.tier].lit++; byTier[s.tier].bonus += t.bonus; byTier[s.tier].gold += t.cost;
      byTierAttr[s.attr][s.tier].lit++; byTierAttr[s.attr][s.tier].bonus += t.bonus;
      byTierAttr[s.attr][s.tier].gold += t.cost;
    }
  }
  let lit = 0, total = 0, bonus = 0, gold = 0;
  ATTR_ORDER.forEach(function(a){
    lit += byAttr[a].lit; total += byAttr[a].total;
    bonus += byAttr[a].bonus; gold += byAttr[a].gold;
  });
  return { lit: lit, total: total, bonus: bonus, gold: gold,
           byAttr: byAttr, byTier: byTier, byTierAttr: byTierAttr, fullBonus: fullBonusTotal() };
}

function viewStats(){
  const st = statsSnapshot();
  const pct = st.total ? Math.round(st.lit / st.total * 100) : 0;

  return '<div class="page statpage">' +
    '<div class="stathead">' +
      '<div class="stathead-title">涂色统计</div>' +
      '<div class="stathead-sub">' + state.data.characters.length + ' 位使徒 · ' + st.total + ' 格</div>' +
    '</div>' +

    /* 顶部主卡：左边已用金蜡笔(主位) / 右边格子解锁环形进度(次位) */
    '<div class="hero2">' +
      '<div class="hero2-main">' +
        '<div class="hero2-row">' +
          '<img class="hero2-icon" src="./assets/ui/crayon.webp" alt="">' +
          '<div class="hero2-num">' + st.gold + '<span>根</span></div>' +
        '</div>' +
        '<div class="hero2-lab">已用金蜡笔</div>' +
      '</div>' +
      '<div class="hero2-side">' +
        '<div class="hero2-ring">' +
          '<svg viewBox="0 0 80 80">' +
            '<defs><linearGradient id="rgGrad" x1="0" y1="0" x2="1" y2="1">' +
              '<stop offset="0" class="rg-g0"/>' +
              '<stop offset="1" class="rg-g1"/>' +
            '</linearGradient></defs>' +
            '<circle class="rg-bg" cx="40" cy="40" r="' + RING_R + '"/>' +
            '<circle class="rg-fg" cx="40" cy="40" r="' + RING_R + '"' +
              ' stroke-dasharray="' + RING_C.toFixed(2) + '"' +
              ' stroke-dashoffset="' + (RING_C * (1 - pct / 100)).toFixed(2) + '"/>' +
          '</svg>' +
          '<div class="hero2-ring-in"><b>' + st.lit + '</b><em>/' + st.total + '</em></div>' +
        '</div>' +
        '<div class="hero2-ring-lab">已解锁格子</div>' +
      '</div>' +
    '</div>' +

    /* 按属性：图标 + 名称 + 细进度 + 数量 + 加成 */
    '<div class="scard">' +
      '<div class="scard-t">按属性</div>' +
      ATTR_ORDER.map(function(a){
        const d = st.byAttr[a];
        const p2 = d.total ? d.lit/d.total*100 : 0;
        return '<div class="arow">' +
          '<img src="./assets/cells/' + ATTR_IMG[a] + '.webp" alt="' + ATTR[a].name + '">' +
          '<span class="arow-name">' + ATTR[a].name + '</span>' +
          '<span class="arow-bar"><i style="width:' + p2.toFixed(1) + '%"></i></span>' +
          '<span class="arow-num">' + d.lit + '/' + d.total + '</span>' +
          '<span class="arow-bonus">+' + d.bonus + '%</span>' +
        '</div>';
      }).join('') +
    '</div>' +

    /* 层 × 属性：每行左侧给出该层整体进度点阵 */
    '<div class="scard grow">' +
      '<div class="scard-t">层数 × 属性</div>' +
      '<div class="matrix">' +
        '<div class="mx-row mx-head">' +
          '<span></span>' +
          TIERS.map(function(t){
            const d = st.byTier[t.n];
            return '<span class="mx-headcol">' +
              '<em>第' + t.n + '层</em>' +
              '<b>' + Math.round(d.lit/d.total*100) + '%</b>' +
            '</span>';
          }).join('') +
        '</div>' +
        ATTR_ORDER.map(function(a){
          return '<div class="mx-row">' +
            '<span class="mx-label"><img src="./assets/cells/' + ATTR_IMG[a] + '.webp" alt=""></span>' +
            TIERS.map(function(t){
              const c = st.byTierAttr[a][t.n];
              const done = c.total > 0 && c.lit === c.total;
              const p3 = c.total ? (c.lit / c.total * 100) : 0;
              return '<span class="mx-cell ' + (done?'done':'') + (c.lit===0?' zero':'') + '">' +
                '<span class="mx-txt">' + c.lit + '<small>/' + c.total + '</small></span>' +
                '<span class="mx-track"><i class="mx-under" style="width:' + p3.toFixed(1) + '%"></i></span>' +
              '</span>';
            }).join('') +
          '</div>';
        }).join('') +
      '</div>' +
    '</div>' +
  '</div>';
}
