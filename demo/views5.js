/* ---------- 渲染路由 ---------- */
function setTab(tab){
  state.tab = tab;
  $$('.tab').forEach(function(b){ b.classList.toggle('on', b.dataset.tab === tab); });
}

function nav(name, params){
  state.route = { name: name, params: params || {} };
  render();
  $('#viewport').scrollTop = 0;
}
function back(){ nav(state.tab); }

function render(){
  const v = $('#viewport');
  const r = state.route.name;
  const tabOf = { home:'home', apostle:'apostle', detail:'apostle', stats:'stats',
                  battlepower:'home', petdispatch:'home', me:'me', saves:'me' };
  if(tabOf[r]) setTab(tabOf[r]);

  let html = '';
  switch(r){
    case 'home':        html = viewHome(); break;
    case 'apostle':     html = viewApostle(); break;
    case 'detail':      html = viewDetail(state.route.params.id); break;
    case 'stats':       html = viewStats(); break;
    case 'battlepower': html = viewBattlePower(); break;
    case 'petdispatch': html = viewPetDispatch(); break;
    case 'me':          html = viewMe(); break;
    case 'saves':       html = viewSaves(); break;
    default:            html = viewHome();
  }
  v.innerHTML = html;
  wire();
}

/* ---------- Sheet ---------- */
function openSheet(html){
  $('#sheetBody').innerHTML = html;
  $('#sheet').classList.add('on');
  $('#backdrop').classList.add('on');
}
function closeSheet(){
  $('#sheet').classList.remove('on');
  $('#backdrop').classList.remove('on');
}
window.closeSheet = closeSheet;
window.openSheet = openSheet;
window.nav = nav;
window.back = back;
window.toast = toast;

/* ---------- 事件绑定 ---------- */
function wire(){
  const q = $('#q');
  if(q){ q.oninput = function(e){ state.filters.q = e.target.value; softRefresh(); }; }

  const sel = $('#pdRegion');
  if(sel){ sel.onchange = function(e){ state.pdForm.region = e.target.value; state.pdResult = null; save(); render(); }; }

  $$('#pdCount button').forEach(function(b){
    b.onclick = function(){
      state.pdForm.taskCount = +b.dataset.n;
      $$('#pdCount button').forEach(function(x){ x.classList.toggle('on', x === b); });
      save();
    };
  });

  $$('[data-pid]').forEach(function(b){
    b.onclick = function(){
      const id = +b.dataset.pid;
      state.selectedPets.has(id) ? state.selectedPets.delete(id) : state.selectedPets.add(id);
      b.classList.toggle('on');
      save();
      const el = $('#pdOwnedCount');
      if(el) el.textContent = state.selectedPets.size;
    };
  });

  $$('[data-bp]').forEach(function(inp){
    inp.oninput = function(e){
      const v = parseFloat(e.target.value);
      state.bpForm[e.target.dataset.bp] = isNaN(v) ? 0 : v;
      softRefresh();
    };
  });

  $$('#tierSeg button').forEach(function(b){
    b.onclick = function(){ switchTier(+b.dataset.tier); };
  });

  const bs = $('#bpSearch');
  if(bs){
    bs.oninput = function(e){
      const v = e.target.value.trim().toLowerCase();
      const box = $('#bpSuggest');
      if(!v){ box.innerHTML = ''; return; }
      const hits = state.data.bp.characters.filter(function(c){
        return c.name.toLowerCase().indexOf(v) >= 0;
      }).slice(0, 8);
      box.innerHTML = hits.length
        ? '<div class="suggest">' + hits.map(function(c){
            return '<div class="sugitem" data-uid="' + c.uid + '">' + esc(c.name) +
              '<span>' + c.attackType + ' · UID ' + c.uid + '</span></div>';
          }).join('') + '</div>'
        : '';
      $$('[data-uid]', box).forEach(function(el){
        el.onclick = function(){
          state.bpForm.uid = +el.dataset.uid;
          render();
          toast('已选择 ' + el.textContent.trim());
        };
      });
    };
  }
}

let _softT;
function softRefresh(){
  clearTimeout(_softT);
  _softT = setTimeout(function(){
    const v = $('#viewport');
    const top = v.scrollTop;
    const el = document.activeElement;
    const focusId = el && el.id ? el.id : null;
    const caret = el && el.selectionStart != null ? el.selectionStart : null;
    render();
    v.scrollTop = top;
    if(focusId){
      const n = document.getElementById(focusId);
      if(n){ n.focus(); if(caret != null){ try { n.setSelectionRange(caret, caret); } catch(e){} } }
    }
  }, 240);
}

/* ---------- 启动 ---------- */
function boot(){
  Promise.all([
    fetch('./data/characters.json').then(function(r){ return r.json(); }),
    fetch('./data/petdispatch.json').then(function(r){ return r.json(); }),
    fetch('./data/battlepower.json').then(function(r){ return r.json(); }),
  ]).then(function(results){
    const chars = results[0], pets = results[1], bp = results[2];
    state.data.characters = chars.characters;
    state.data.pets = pets.pets;
    state.data.regions = pets.regions;
    state.data.bp = {
      characters: bp.characters.map(function(c){
        return Object.assign({}, c, {
          activeSkillValueA: +c.activeSkillValueA,
          ultimateSkillValueA: +c.ultimateSkillValueA,
          passiveValueA: +c.passiveValueA,
          weightValueA: +c.weightValueA,
          asideValueA: +c.asideValueA,
        });
      }),
    };
    state.allSlots = buildSlots();

    if(!load()) seedProgress();
    if(!state.pdForm.region) state.pdForm.region = state.data.regions[0].name;
    if(state.selectedPets.size === 0){
      state.data.pets.slice(0, 14).forEach(function(pet){ state.selectedPets.add(pet.id); });
    }
    if(!state.bpForm.uid){
      const def = state.data.bp.characters.find(function(c){ return c.uid === 10001; });
      state.bpForm.uid = def ? def.uid : state.data.bp.characters[0].uid;
    }

    const tick = function(){
      $('#sbTime').textContent = new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' });
    };
    tick(); setInterval(tick, 30000);

    const routes = [
      ['首页', 'home', ''],
      ['使徒列表', 'apostle', ''],
      ['使徒详情 · 伊德', 'detail', 'id=ed'],
      ['统计', 'stats', ''],
      ['战斗力计算', 'battlepower', ''],
      ['宠物派遣', 'petdispatch', ''],
      ['我的', 'me', ''],
      ['存档中心', 'saves', ''],
    ];
    $('#stageList').innerHTML = routes.map(function(r){
      return '<button class="stage-item" data-r="' + r[1] + '" data-p="' + r[2] + '">' +
        '<span>' + r[0] + '</span></button>';
    }).join('');

    $$('.stage-item').forEach(function(b){
      b.onclick = function(){
        const params = {};
        if(b.dataset.p) b.dataset.p.split(',').forEach(function(kv){
          const kvp = kv.split('='); params[kvp[0]] = kvp[1];
        });
        nav(b.dataset.r, params);
        markStage(b);
      };
    });

    $('#backdrop').onclick = closeSheet;
    $$('.tab').forEach(function(b){
      b.onclick = function(){ nav(b.dataset.tab); markStage(null); };
    });

    const applyDeepLink = function(){
      const h = (location.hash || '').replace(/^#\/?/, '');
      const qs = new URLSearchParams(location.search);
      let route = qs.get('route') || '';
      let id = qs.get('id') || '';
      if(h){
        const parts = h.split('/');
        route = parts[0];
        if(parts[1]) id = parts[1];
      }
      if(!route) return;
      if(route === 'detail'){ nav('detail', { id: id || 'ed' }); }
      else if(['home','apostle','stats','me','battlepower','petdispatch','saves'].indexOf(route) >= 0){
        nav(route);
      }
    };
    applyDeepLink();
    window.addEventListener('hashchange', applyDeepLink);

    render();

    if(new URLSearchParams(location.search).get('autopd')){
      selectAllPets(true);
      setTimeout(function(){
        runPetDispatch();
        setTimeout(function(){
          const v = $('#viewport');
          if(v) v.scrollTop = v.scrollHeight;
        }, 120);
      }, 80);
    }
  }).catch(function(err){
    document.getElementById('viewport').innerHTML =
      '<div class="page" style="padding-top:80px;text-align:center;color:#999;font-size:13px;line-height:1.8">' +
      '加载失败：' + esc(err.message) + '<br><br>请用本地 HTTP 服务打开本页' +
      '<br><span style="font-size:11px;opacity:.7">npx serve .</span></div>';
  });
}

function markStage(btn){
  $$('.stage-item').forEach(function(b){ b.classList.toggle('on', b === btn); });
  if(!btn){
    const map = { home:'home', apostle:'apostle', stats:'stats', me:'me' };
    const cur = map[state.route.name];
    $$('.stage-item').forEach(function(b){ b.classList.toggle('on', b.dataset.r === cur); });
  }
}

boot();
