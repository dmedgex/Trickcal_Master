# -*- coding: utf-8 -*-
"""把 Trickcal_dice 的精确策略表转成 Android 可直接读取的分块二进制。

源数据（Trickcal_dice/solver_data）：
    F.npz     (256, 57)            float64  全状态最优期望
    ACTK.npz  (256, 57, 3, 756)    int16    保留动作编号，-1 表示"登记"
    ACTC.npz  (256, 57, 756)       int8     登记时选哪个类别

未压缩合计约 77MB，不能整表放进 APK 常驻内存。实际使用时一次只查一个
mask（已登记哪些格子对应的位集合），因此按 mask 切块压缩，运行时按需解压
单块（int16 x 57 x 3 x 756 = 258KB），内存峰值低于 1MB。

输出：
    app/src/main/assets/dice/tables.bin
    app/src/test/resources/dice/policy_cases.json   （决策对拍基准）

二进制布局（小端）：
    magic        8B   "DICETBL1"
    version      i32  1
    maskCount    i32  256
    stateCount   i32  756
    catCount     i32  8
    keepRowCount i32  1470
    vectorCount  i32  126
    --- 定长区 ---
    F            f64 x 256 x 57
    SC           i16 x 756 x 8
    keepRows     u8 x 1470 x 6   + i8 x 1470      （橙骰多重集 + 绿骰，0=不保留绿骰）
    vectorIndex  u8 x 126 x 6    + u16 x 126      （橙骰多重集 -> 状态索引）
    actkLens     i32 x 256
    actcLens     i32 x 256
    --- 变长区 ---
    actk blocks  zlib(i16 x 57 x 3 x 756) x 256
    actc blocks  zlib(i8  x 57 x 756)     x 256
"""
import json
import os
import random
import struct
import sys
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DICE_SRC = os.path.join(os.path.dirname(ROOT), "Trickcal_dice")

ASSET_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "dice")
TEST_DIR = os.path.join(ROOT, "app", "src", "test", "resources", "dice")

MAGIC = b"DICETBL1"
VERSION = 1
N_MASK = 256
N_CATS = 8
N_ORANGE = 4
FACES = 6
UPPER_MAX = 57
REROLL_MAX = 3

sys.path.insert(0, DICE_SRC)


def count_vectors(total):
    """所有 6 维非负整数向量，各分量之和 = total（顺序必须与 solver_policy 一致）"""
    res = []

    def rec(i, rem, cur):
        if i == FACES:
            if rem == 0:
                res.append(tuple(cur))
            return
        for k in range(rem + 1):
            cur.append(k)
            rec(i + 1, rem - k, cur)
            cur.pop()

    rec(0, total, [])
    return res


def build_score_table(states):
    """复制 solver_policy.build_score_table，得到 (756, 8) 的整数分表"""
    def score_eff(cc, cat):
        if cat < 5:
            return (cat + 2) * cc[cat + 1]
        if cat == 5:
            nz = [k for k in cc if k]
            return sum((i + 1) * cc[i] for i in range(FACES)) if sorted(nz, reverse=True) == [3, 2] else 0
        if cat == 6:
            if all(cc[i] == 1 for i in range(5)) or all(cc[i] == 1 for i in range(1, 6)):
                return 30
            return 0
        return 50 if max(cc) == 5 else 0

    sc = np.zeros((len(states), N_CATS), dtype=np.int16)
    for i, (c, g) in enumerate(states):
        opts = range(1, FACES + 1) if g == 1 else [g]
        for cat in range(N_CATS):
            best = 0
            for a in opts:
                cc = list(c)
                cc[a - 1] += 1
                v = score_eff(cc, cat)
                if v > best:
                    best = v
            sc[i, cat] = best
    return sc


def build_keep_rows():
    """复制 solver_policy.build_keep_transition 的行顺序：(保留橙骰多重集, 保留的绿骰点数)"""
    rows = []
    for m in range(0, N_ORANGE + 1):
        for kc in count_vectors(N_ORANGE - m):
            for gn in [None] + list(range(1, FACES + 1)):
                rows.append((kc, gn))
    return rows


def main():
    os.makedirs(ASSET_DIR, exist_ok=True)
    os.makedirs(TEST_DIR, exist_ok=True)

    f_tbl = np.load(os.path.join(DICE_SRC, "solver_data", "F.npz"))["a"].astype("<f8")
    actk = np.load(os.path.join(DICE_SRC, "solver_data", "ACTK.npz"))["a"].astype("<i2")
    actc = np.load(os.path.join(DICE_SRC, "solver_data", "ACTC.npz"))["a"].astype("<i1")

    assert f_tbl.shape == (N_MASK, UPPER_MAX), f_tbl.shape
    assert actk.shape == (N_MASK, UPPER_MAX, REROLL_MAX, 756), actk.shape
    assert actc.shape == (N_MASK, UPPER_MAX, 756), actc.shape

    states = [(c, g) for c in count_vectors(N_ORANGE) for g in range(1, FACES + 1)]
    assert len(states) == 756, len(states)
    sc = build_score_table(states)

    vectors = count_vectors(N_ORANGE)
    keep_rows = build_keep_rows()

    actk_blocks, actc_blocks = [], []
    for mask in range(N_MASK):
        actk_blocks.append(zlib.compress(actk[mask].tobytes(), 9))
        actc_blocks.append(zlib.compress(actc[mask].tobytes(), 9))

    buf = bytearray()
    buf += MAGIC
    buf += struct.pack("<iiiiii", VERSION, N_MASK, 756, N_CATS, len(keep_rows), len(vectors))
    buf += f_tbl.tobytes()
    buf += sc.tobytes()
    for kc, _gn in keep_rows:
        buf += bytes(kc)
    buf += bytes(0 if gn is None else gn for _kc, gn in keep_rows)
    for v in vectors:
        buf += bytes(v)
    for i, _v in enumerate(vectors):
        buf += struct.pack("<H", i)
    buf += struct.pack("<" + "i" * N_MASK, *[len(b) for b in actk_blocks])
    buf += struct.pack("<" + "i" * N_MASK, *[len(b) for b in actc_blocks])
    for b in actk_blocks:
        buf += b
    for b in actc_blocks:
        buf += b

    out_bin = os.path.join(ASSET_DIR, "tables.bin")
    with open(out_bin, "wb") as f:
        f.write(buf)

    actk_raw = int(actk.nbytes)
    actc_raw = int(actc.nbytes)
    print("[tables] 源表未压缩: ACTK %.1fMB + ACTC %.1fMB" % (actk_raw / 1048576, actc_raw / 1048576))
    print("[tables] 输出 %s  %.2fMB" % (out_bin, len(buf) / 1048576))
    print("[tables] 单块最大(压缩前) ACTK %dKB / ACTC %dKB"
          % (actk[0].nbytes // 1024, actc[0].nbytes // 1024))

    meta = dict(
        version=VERSION,
        masks=N_MASK,
        states=756,
        categories=N_CATS,
        keepRows=len(keep_rows),
        vectors=len(vectors),
        bytes=len(buf),
        actkRawBytes=actk_raw,
        actcRawBytes=actc_raw,
    )
    with open(os.path.join(HERE, "dice_tables_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    # ---------------- 数字模板（左上角 N/8 计数器） ----------------
    dz = np.load(os.path.join(DICE_SRC, "templates", "digits.npz"))
    glyphs = dz["glyphs"].astype(np.uint8)
    labels = dz["labels"].astype("<i4")
    gcount, gh, gw = glyphs.shape
    dbuf = bytearray()
    dbuf += b"DICEDIG1"
    dbuf += struct.pack("<iii", gcount, gw, gh)
    dbuf += glyphs.tobytes()
    dbuf += labels.tobytes()
    out_dig = os.path.join(ASSET_DIR, "digits.bin")
    with open(out_dig, "wb") as f:
        f.write(dbuf)
    print("[digits] %d 个字形 %dx%d -> %s (%.1fKB)"
          % (gcount, gw, gh, out_dig, len(dbuf) / 1024))

    # ---------------- 决策对拍基准 ----------------
    import solver_policy

    policy = solver_policy.get_policy()
    rng = random.Random(20261104)
    cases = []
    skipped = 0
    while len(cases) < 300:
        used = sorted(rng.sample(range(N_CATS), rng.randint(0, 7)))
        upper = rng.randint(0, 56)
        rerolls = rng.randint(0, 2)
        orange = [rng.randint(1, FACES) for _ in range(N_ORANGE)]
        green = rng.randint(1, FACES)
        r = policy.advise(set(used), upper, rerolls, orange, green)
        if r["action"] == "score":
            if r["category"] < 0:
                skipped += 1
                continue
            cases.append(dict(
                used=used, upper=upper, rerolls=rerolls,
                orange=orange, green=green,
                action="score", category=int(r["category"]), points=int(r["points"]),
            ))
        else:
            cases.append(dict(
                used=used, upper=upper, rerolls=rerolls,
                orange=orange, green=green,
                action="keep",
                keep=[int(v) for v in r["keep"]],
                keepGreen=0 if r["keep_green"] is None else int(r["keep_green"]),
            ))

    out_tsv = os.path.join(TEST_DIR, "policy_cases.tsv")
    with open(out_tsv, "w", encoding="utf-8", newline="\n") as f:
        f.write("# used\tupper\trerolls\torange\tgreen\taction\tcategory\tpoints\tkeep\tkeepGreen\n")
        for c in cases:
            f.write("\t".join([
                ",".join(str(v) for v in c["used"]),
                str(c["upper"]),
                str(c["rerolls"]),
                ",".join(str(v) for v in c["orange"]),
                str(c["green"]),
                c["action"],
                str(c.get("category", -1)),
                str(c.get("points", 0)),
                ",".join(str(v) for v in c.get("keep", [])),
                str(c.get("keepGreen", 0)),
            ]) + "\n")
    print("[cases] 对拍基准 %d 条, 跳过 %d 条 -> %s" % (len(cases), skipped, out_tsv))
    score_n = sum(1 for c in cases if c["action"] == "score")
    print("[cases] 其中登记 %d 条 / 重投 %d 条" % (score_n, len(cases) - score_n))


if __name__ == "__main__":
    main()
