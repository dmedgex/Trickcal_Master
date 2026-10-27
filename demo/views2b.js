/* ---------- 筛选 sheet（层数与格子已常驻列表上方，这里只补性格） ---------- */
window.openFilter = function(){
  const f = state.filters;
  openSheet('<h3 class="sheet-title">按性格筛选</h3>' +
    '<p class="sheet-desc">层数与格子已经在列表上方常驻，这里只补充性格条件。</p>' +
    '<div class="fgroup"><div class="fchips">' +
      Object.keys(PERSONALITY).map(function(k){
        const v = PERSONALITY[k];
        return '<button class="fchip ' + (f.pers===k?'on':'') + '" data-fpers="' + k + '">' +
          '<span class="dot" style="background:' + v.color + '"></span>' + v.label + '</button>';
      }).join('') +
    '</div></div>' +
    '<div class="btnrow" style="margin-top:18px">' +
      '<button class="btn btn-ghost" onclick="state.filters.pers=null;closeSheet();render()">不限</button>' +
      '<button class="btn btn-primary" onclick="closeSheet();render()">应用</button>' +
    '</div>');
  $$('[data-fpers]').forEach(function(b){
    b.onclick = function(){
      const k = b.dataset.fpers;
      f.pers = (f.pers === k) ? null : k;
      $$('[data-fpers]').forEach(function(x){ x.classList.toggle('on', x.dataset.fpers === f.pers); });
    };
  });
};
