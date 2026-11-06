# -*- coding: utf-8 -*-
"""用 Trickcal_dice 的 vision.py 生成逐帧识别基准（JSON）。

用途：Kotlin 侧的 DiceVision 在真机/模拟器上跑同一批 fixture 时，
逐字段与这份基准比对 —— 骰子颜色、点数、万能标记、槽位、计分板占用、N/8、场景。

基准本身的正确性来自脚本本体（该脚本已在实机跑通），因此这份 JSON 就是
「移植是否等价」的判据，而不是另写一份对照实现。
"""
import json
import os
import sys

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DICE_SRC = os.path.join(os.path.dirname(ROOT), "Trickcal_dice")
sys.path.insert(0, DICE_SRC)

import config as C          # noqa: E402
import vision                # noqa: E402

CAT_KEYS = ["TWOS", "THREES", "FOURS", "FIVES", "SIXES", "FULL_HOUSE", "LARGE_STRAIGHT", "YACHT"]
STATUS = {
    "filled": "FILLED",
    "empty": "EMPTY",
    "overlay": "OVERLAY",
    "green": "GREEN",
}


# recon_match.png 是原脚本识图调试时人工调色的帧，不是真实游戏画面。
# 它的计分板被调成浅蓝，米色判据必然落空 —— 正确行为是判 UNKNOWN 并暂停，
# 而不是为它扩展识别规则。这里保留这张帧，用来验证「异常画面不会被误操作」。
COLOR_ADJUSTED = {"recon_match"}


def mean(im, x, y, r=4):
    return im[y - r:y + r + 1, x - r:x + r + 1].reshape(-1, 3).mean(axis=0)


def classify(im):
    """与 Kotlin 侧 DiceVision.classify 同一套判据，返回 (场景, 是否我方回合)。

    Python 里 im 是 BGR：下标 0=B 1=G 2=R；Kotlin 侧 Mat 是 RGB，比较时顺序相反。
    """
    paper = mean(im, *C.P_SCOREBOARD)
    center = mean(im, *C.P_CENTER)
    tan = mean(im, *C.P_TAN)
    if 165 <= paper[0] <= 215 and 205 <= paper[1] <= 245 and 235 <= paper[2] <= 255:
        bg = mean(im, *C.P_BG_TOP)
        r = bg[2]
        if r < 60:
            return "MATCH", False
        if r > 120:
            return "MATCH", True
        return "UNKNOWN", False
    if 120 <= center[0] <= 185 and 180 <= center[1] <= 225 and 215 <= center[2] <= 255:
        return "RESULT", False
    if center[0] >= 200 and center[1] >= 205 and center[2] >= 235 and tan[2] < 120 and tan[0] < 120:
        return "VICTORY", False
    if 40 <= tan[0] <= 120 and 130 <= tan[1] <= 200 and 200 <= tan[2] <= 255:
        return "MAIN", False
    return "UNKNOWN", False


def board_rect(im):
    """与 Kotlin 侧 DiceGeometry.detectBoard 同样的列/行投影"""
    h, w = im.shape[:2]
    hsv = cv2.cvtColor(im, cv2.COLOR_BGR2HSV)
    m = cv2.inRange(hsv, (0, 0, 228), (180, 255, 255))
    m[:, int(w * 0.45):] = 0

    def dilate1d(a, r):
        b = a.copy()
        for d in range(1, r + 1):
            b[:-d] = np.maximum(b[:-d], a[d:])
            b[d:] = np.maximum(b[d:], a[:-d])
        return b

    col = dilate1d(m.sum(0) / 255.0, 6)
    ok = col > h * 0.32
    best, s = (0, 0), None
    for i, v in enumerate(list(ok) + [False]):
        if v and s is None:
            s = i
        elif not v and s is not None:
            if i - s > best[1] - best[0]:
                best = (s, i)
            s = None
    x0, x1 = best
    if x1 - x0 < 50:
        return None
    row = dilate1d(m[:, x0:x1].sum(1) / 255.0, 6)
    ok2 = row > (x1 - x0) * 0.40
    ys = [i for i, v in enumerate(ok2) if v]
    return [x0, min(ys), x1, max(ys)] if ys else None


def die_json(d):
    return dict(
        kind="ORANGE" if d["color"] == "orange" else "GREEN",
        value=d["value"], x=int(d["x"]), y=int(d["y"]),
    )


def cat_key(k):
    """脚本的类别键是 2/3/4/5/6/fh/ls/yacht；整数键表示点数，不是数组下标。"""
    if isinstance(k, int):
        return CAT_KEYS[k - 2]
    return {"fh": "FULL_HOUSE", "ls": "LARGE_STRAIGHT", "yacht": "YACHT"}[k]


def main():
    fixtures = sorted(
        f for f in os.listdir(os.path.join(DICE_SRC, "fixtures")) if f.endswith(".png")
    )
    frames = []
    for fn in fixtures:
        path = os.path.join(DICE_SRC, "fixtures", fn)
        im = cv2.imread(path)
        # 与 Kotlin 的 DiceVision.analyze 同序：弹窗判定排在场景分类之前
        if vision.detect_exit_dialog(im):
            scene, my_turn = "DIALOG", False
        else:
            scene, my_turn = classify(im)
        det = vision.read_occupancy_detail(im)
        occ = {cat_key(k): STATUS[v["status"]] for k, v in det.items()} if scene == "MATCH" else {}
        item = dict(
            name=fn[:-4],
            width=int(im.shape[1]),
            height=int(im.shape[0]),
            scene=scene,
            isMyTurn=my_turn,
            board=board_rect(im),
            occupancy=occ,
        )
        if fn[:-4] in COLOR_ADJUSTED:
            item["note"] = "人工调色帧（识图调试用），非真实游戏画面；期望判 UNKNOWN，不作骰子基准"
        if scene == "MATCH":
            table, slots = vision.read_dice(im)
            rc = vision.read_round_counter(im)
            item["table"] = [die_json(d) for d in table]
            item["slots"] = [die_json(d) for d in slots]
            item["round"] = None if rc is None else list(rc)
        frames.append(item)
        print("%-14s %-8s 我方=%-5s 桌%d 槽%d 已登记%d N/8=%s" % (
            item["name"], scene, my_turn,
            len(item.get("table", [])), len(item.get("slots", [])),
            sum(1 for v in occ.values() if v == "FILLED"),
            item.get("round"),
        ))

    out = dict(
        baseWidth=1280,
        baseHeight=720,
        note="由 tools/gen_dice_baseline.py 生成；Kotlin 侧 DiceVision 需逐字段一致",
        frames=frames,
    )
    dst = os.path.join(HERE, "dice_baseline.json")
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("\n[baseline] -> %s" % dst)


if __name__ == "__main__":
    main()
