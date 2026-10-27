/* ---------- 首页（去掉"继续使用"） ---------- */
function greeting(){
  const h = new Date().getHours();
  if(h < 6) return '夜深了';
  if(h < 11) return '早上好';
  if(h < 14) return '中午好';
  if(h < 18) return '下午好';
  return '晚上好';
}
function timeAgo(ts){
  const m = Math.floor((Date.now()-ts)/60000);
  if(m < 1) return '刚刚';
  if(m < 60) return m + ' 分钟前';
  const h = Math.floor(m/60);
  if(h < 24) return h + ' 小时前';
  return Math.floor(h/24) + ' 天前';
}

function viewHome(){
  const g = globalStats();
  const pct = Math.round(g.pct*100);
  const C = 2*Math.PI*38;

  const tiles = [
    { k:'crayon', ico:'🖍️', bg:'#FBEAE1', name:'蜡笔涂色', desc:'使徒棋盘解锁进度', go:"nav('apostle')" },
    { k:'stats',  ico:'📊', bg:'#EDF2F8', name:'涂色统计', desc:'每层每属性明细', go:"nav('stats')" },
    { k:'pet', ico:'🐾', bg:'#E7F4EC', name:'宠物派遣', desc:'最优派遣方案', go:"nav('petdispatch')", badge:'BETA', badgeCls:'new' },
    { k:'bp', ico:'⚔️', bg:'#F1EAFB', name:'战斗力计算', desc:'角色战力换算', go:"nav('battlepower')", badge:'BETA', badgeCls:'new' },
  ];
  const soon = [
    { ico:'💥', bg:'#FBEAE3', name:'伤害计算', desc:'BETA · 待接入' },
    { ico:'👁️', bg:'#EDF2F8', name:'识图与操作', desc:'跨应用自动化' },
  ];

  return '<div class="page">' +
    '<div class="hero">' +
      '<div class="hero-hello">' + greeting() + '，指挥官</div>' +
      '<div class="hero-title">今天也给<em>金蜡笔</em>上点色？</div>' +
    '</div>' +

    '<div class="ringcard">' +
      '<div class="ring">' +
        '<svg width="92" height="92">' +
          '<circle cx="46" cy="46" r="38" fill="none" stroke="var(--surface-2)" stroke-width="8"/>' +
          '<circle cx="46" cy="46" r="38" fill="none" stroke="var(--brand)" stroke-width="8" ' +
            'stroke-linecap="round" stroke-dasharray="' + C + '" ' +
            'stroke-dashoffset="' + (C*(1-g.pct)) + '" ' +
            'style="transition:stroke-dashoffset .8s cubic-bezier(.22,.61,.36,1)"/>' +
        '</svg>' +
        '<div class="ring-val">' + pct + '<small>%</small></div>' +
      '</div>' +
      '<div class="ring-info">' +
        '<h3>涂色进度</h3>' +
        '<p>已点亮 <b>' + g.lit + '</b> / ' + g.total + ' 格 · 共 ' + state.data.characters.length + ' 位使徒</p>' +
        '<div class="ring-chips">' +
          '<div class="chip"><span class="dot" style="background:var(--gold)"></span><b>+' + g.bonus + '%</b> 加成</div>' +
          '<div class="chip"><span class="dot" style="background:var(--brand)"></span><b>' + g.gold + '</b> 金蜡笔</div>' +
        '</div>' +
      '</div>' +
    '</div>' +

    '<div class="sec">' +
      '<div class="sec-head">' +
        '<div class="sec-title">全部功能</div>' +
        '<button class="sec-more" onclick="toast(\'功能管理 · 可拖拽排序 / 置顶 / 隐藏\')">管理</button>' +
      '</div>' +
      '<div class="grid">' +
        tiles.map(function(t){
          return '<button class="tile" onclick="' + t.go + '">' +
            (t.badge ? '<div class="tile-badge ' + (t.badgeCls||'') + '">' + t.badge + '</div>' : '') +
            '<div class="tile-ico" style="background:' + t.bg + '">' + t.ico + '</div>' +
            '<div class="tile-name">' + t.name + '</div>' +
            '<div class="tile-desc">' + t.desc + '</div>' +
          '</button>';
        }).join('') +
      '</div>' +
    '</div>' +

    '<div class="sec">' +
      '<div class="sec-head"><div class="sec-title">即将上线</div></div>' +
      '<div class="grid">' +
        soon.map(function(t){
          return '<button class="tile" onclick="toast(\'' + t.name + ' · 规划中\')" style="opacity:.62">' +
            '<div class="tile-badge soon">规划</div>' +
            '<div class="tile-ico" style="background:' + t.bg + '">' + t.ico + '</div>' +
            '<div class="tile-name">' + t.name + '</div>' +
            '<div class="tile-desc">' + t.desc + '</div>' +
          '</button>';
        }).join('') +
      '</div>' +
    '</div>' +

    '<div class="sec" style="margin-top:22px">' +
      '<button class="card savebar" onclick="nav(\'saves\')">' +
        '<div class="meitem-ico" style="background:#E7F4EC">☁️</div>' +
        '<div style="flex:1">' +
          '<div class="savebar-t">上次备份 · ' + timeAgo(state.saveInfo.lastBackup) + '</div>' +
          '<div class="savebar-d">共 ' + state.saveInfo.backups + ' 个自动快照</div>' +
        '</div>' +
        '<div class="meitem-arrow">›</div>' +
      '</button>' +
    '</div>' +
  '</div>';
}

/* ---------- 我的 ---------- */
function viewMe(){
  const g = globalStats();
  return '<div class="page">' +
    '<div class="hero">' +
      '<div class="hero-hello">账户</div>' +
      '<div class="hero-title">我的</div>' +
    '</div>' +

    '<div class="mecard">' +
      '<div class="mecard-ico">🖍️</div>' +
      '<div style="flex:1">' +
        '<h3>本地用户</h3>' +
        '<p>离线运行 · 无需登录 · 数据仅存本机</p>' +
      '</div>' +
    '</div>' +

    '<div class="savecard">' +
      '<h4>☁️ 存档中心</h4>' +
      '<p>当前进度：' + g.lit + '/' + g.total + ' 格 · 上次备份 ' + timeAgo(state.saveInfo.lastBackup) + '</p>' +
      '<div class="btnrow">' +
        '<button class="btn btn-ghost" onclick="nav(\'saves\')">管理存档</button>' +
        '<button class="btn btn-primary" onclick="doBackup()">立即备份</button>' +
      '</div>' +
    '</div>' +

    '<div class="melist">' +
      '<button class="meitem" onclick="nav(\'saves\')">' +
        '<div class="meitem-ico" style="background:#E7F4EC">💾</div>' +
        '<div class="meitem-main"><div class="meitem-t">导出与导入</div>' +
        '<div class="meitem-d">新存档格式 · 兼容旧版 JSON</div></div>' +
        '<div class="meitem-arrow">›</div></button>' +
      '<button class="meitem" onclick="toast(\'外观设置 · 深色/浅色/跟随系统\')">' +
        '<div class="meitem-ico" style="background:#F1EAFB">🎨</div>' +
        '<div class="meitem-main"><div class="meitem-t">外观</div>' +
        '<div class="meitem-d">跟随系统</div></div>' +
        '<div class="meitem-arrow">›</div></button>' +
      '<button class="meitem" onclick="toast(\'功能管理 · 排序 / 置顶 / 隐藏\')">' +
        '<div class="meitem-ico" style="background:#FBEAE1">🧩</div>' +
        '<div class="meitem-main"><div class="meitem-t">功能管理</div>' +
        '<div class="meitem-d">调整首页顺序与可见性</div></div>' +
        '<div class="meitem-arrow">›</div></button>' +
    '</div>' +

    '<div class="melist">' +
      '<button class="meitem" onclick="toast(\'检查更新\')">' +
        '<div class="meitem-ico" style="background:#EDF2F8">🔄</div>' +
        '<div class="meitem-main"><div class="meitem-t">检查更新</div>' +
        '<div class="meitem-d">当前 v2.0.0 (原型)</div></div>' +
        '<div class="meitem-arrow">›</div></button>' +
      '<button class="meitem" onclick="toast(\'这是只读原型，数据不会被修改\')">' +
        '<div class="meitem-ico" style="background:#F4EFE8">ℹ️</div>' +
        '<div class="meitem-main"><div class="meitem-t">关于</div>' +
        '<div class="meitem-d">UI 原型 · 数据来自 v1.1.5</div></div>' +
        '<div class="meitem-arrow">›</div></button>' +
    '</div>' +
  '</div>';
}

/* ---------- 存档中心 ---------- */
function viewSaves(){
  const g = globalStats();
  const rows = [
    ['涂色进度', g.lit + ' / ' + g.total + ' 格'],
    ['自定义使徒', '0 位'],
    ['派遣选择', state.selectedPets.size + ' 只'],
    ['任务特性自定义', Object.keys(state.pdForm.overrides).length + ' 条'],
    ['存档大小', '约 12 KB'],
    ['最后写入', timeAgo(Date.now() - 1000*60*60*26)],
  ];

  return '<div class="page">' +
    '<div style="padding:6px 0 10px">' +
      '<button onclick="back()" class="backlink">‹ 我的</button>' +
    '</div>' +
    '<div class="hero" style="padding:2px 0 16px">' +
      '<div class="hero-title" style="font-size:23px">存档中心</div>' +
    '</div>' +

    '<div class="card" style="padding:16px;margin-bottom:14px">' +
      '<div class="rowhead" style="margin-bottom:13px">' +
        '<div style="font-size:13px;font-weight:750">当前存档</div>' +
        '<span class="syncbadge">已同步</span>' +
      '</div>' +
      rows.map(function(r){
        return '<div class="kvrow">' +
          '<span>' + r[0] + '</span>' +
          '<b>' + r[1] + '</b></div>';
      }).join('') +
    '</div>' +

    '<div class="sec" style="margin-top:18px">' +
      '<div class="sec-head"><div class="sec-title">导出</div></div>' +
      '<div class="btnrow" style="margin-bottom:10px">' +
        '<button class="btn btn-primary" onclick="doExport()">导出存档</button>' +
        '<button class="btn btn-ghost" onclick="doBackup()">创建快照</button>' +
      '</div>' +
      '<div class="tip" style="padding:0 2px">' +
        '.trickcal 为 zip 包，含存档 JSON 与自定义图片原文件（不再 base64）。</div>' +
    '</div>' +

    '<div class="sec">' +
      '<div class="sec-head"><div class="sec-title">导入</div></div>' +
      '<div class="melist" style="margin-bottom:0">' +
        '<button class="meitem" onclick="openImportNew()">' +
          '<div class="meitem-ico" style="background:#E7F4EC">📥</div>' +
          '<div class="meitem-main"><div class="meitem-t">导入新存档</div>' +
          '<div class="meitem-d">.trickcal 格式</div></div>' +
          '<div class="meitem-arrow">›</div></button>' +
        '<button class="meitem" onclick="openImportLegacy()">' +
          '<div class="meitem-ico" style="background:#FBEAE1">🗂️</div>' +
          '<div class="meitem-main"><div class="meitem-t">从旧版本导入</div>' +
          '<div class="meitem-d">兼容 v1.x 的三种 JSON 导出</div></div>' +
          '<div class="meitem-arrow">›</div></button>' +
      '</div>' +
    '</div>' +

    '<div class="sec">' +
      '<div class="sec-head"><div class="sec-title">自动快照</div>' +
        '<span style="font-size:10.5px;color:var(--ink-3)">保留最近 10 份</span></div>' +
      '<div class="melist" style="margin-bottom:0">' +
        [0,1,2].map(function(i){
          const ts = Date.now() - (i===0 ? 1000*60*60*26 : i===1 ? 1000*60*60*74 : 1000*60*60*150);
          return '<button class="meitem" onclick="toast(\'将回滚到该快照（原型）\')">' +
            '<div class="meitem-ico" style="background:var(--surface-2)">🕐</div>' +
            '<div class="meitem-main">' +
              '<div class="meitem-t">' + new Date(ts).toLocaleString('zh-CN', { month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit' }) + '</div>' +
              '<div class="meitem-d">' + g.lit + ' 格 · 自动</div></div>' +
            '<div class="meitem-arrow">›</div></button>';
        }).join('') +
      '</div>' +
    '</div>' +
  '</div>';
}

window.doBackup = function(){
  state.saveInfo.lastBackup = Date.now();
  state.saveInfo.backups = Math.min(10, state.saveInfo.backups + 1);
  toast('已创建快照');
  render();
};

window.doExport = function(){
  const payload = {
    format:'trickcal-save', schemaVersion:1, appVersion:'2.0.0-demo', savedAt: Date.now(),
    modules:{
      crayon:{ version:1, data:{ litSlotIds: Array.from(state.lit) } },
      petDispatch:{ version:1, data:{ region: state.pdForm.region, taskCount: state.pdForm.taskCount,
                                      taskBonusSkillOverrides: state.pdForm.overrides } },
    },
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type:'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'trickcal-save-demo.json';
  a.click();
  setTimeout(function(){ URL.revokeObjectURL(a.href); }, 3000);
  toast('已导出存档');
};

window.openImportNew = function(){
  openSheet('<h3 class="sheet-title">导入新存档</h3>' +
    '<p class="sheet-desc">选择 .trickcal 文件。导入前会预览变更，不会静默覆盖。</p>' +
    '<input type="file" id="fileNew" accept=".json,.trickcal" class="input" style="padding:11px">' +
    '<div class="btnrow" style="margin-top:16px">' +
      '<button class="btn btn-ghost" onclick="closeSheet()">取消</button>' +
      '<button class="btn btn-primary" onclick="toast(\'原型不支持真实导入\');closeSheet()">导入</button>' +
    '</div>');
};

window.openImportLegacy = function(){
  openSheet('<h3 class="sheet-title">从旧版本导入</h3>' +
    '<p class="sheet-desc">自动识别 v1.x 的三种导出格式。旧版格子标识依赖出现序号，会通过 v1.1.5 冻结表映射到新 ID。</p>' +
    '<div class="fgroup"><div class="fglabel">支持的旧版导出</div>' +
      '<div style="display:flex;flex-direction:column;gap:7px">' +
        [['设置 → 导出进度','litSlotIds · customApostles'],
         ['派遣 → 导出配置','selection · customPets'],
         ['派遣 → 导出宠物','pets']].map(function(row){
          return '<div class="srcrow">' +
            '<span style="font-size:14px">📄</span>' +
            '<div style="flex:1"><div class="srcrow-t">' + row[0] + '</div>' +
            '<div class="srcrow-d">' + row[1] + '</div></div></div>';
        }).join('') +
      '</div>' +
    '</div>' +
    '<div class="previewbox">' +
      '将导入：<b>702</b> 格中的 <b>238</b> 个已点亮 · <b>3</b> 个自定义使徒 · <b>2</b> 只自定义宠物<br>' +
      '识别不到的 <b>0</b> 个格子会被跳过并列出。</div>' +
    '<p class="sheet-desc" style="margin-top:12px;margin-bottom:0">写入前会自动创建快照，可随时回滚。</p>' +
    '<div class="btnrow" style="margin-top:16px">' +
      '<button class="btn btn-ghost" onclick="closeSheet()">取消</button>' +
      '<button class="btn btn-primary" onclick="closeSheet();toast(\'原型不支持真实导入\')">确认导入</button>' +
    '</div>');
};
