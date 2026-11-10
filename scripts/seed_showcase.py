"""造一份「面试演示用」的完整数据：一个宿舍 4 个人，9 张单子覆盖全部状态。

覆盖到的展示点：
  - 进行中：2 张待接（其中 1 张已超时标红）、1 张已接、1 张已取
  - 我接的单：1 张已取（等对方确认）、1 张已完成
  - 历史：2 张已送达、1 张已取消
  - 站内提醒：小明有未读红点（别人接了他的单 / 取了件 / 送到了）

用法：
    python scripts/seed_showcase.py --base http://8.137.39.80:8080
最后一行打印 JSON（房间码 + 成员 id + 统计），把房间码记下来演示用。
"""
import argparse
import http.cookiejar
import json
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8080"


def new_client():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar)), jar


def call(opener, method, path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    try:
        with opener.open(req, timeout=30) as r:
            raw = r.read().decode("utf-8")
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def member_id(jar):
    for c in jar:
        if c.name == "pm_member":
            return int(c.value)
    return None


def fmt(dt):
    return dt.strftime("%Y-%m-%dT%H:%M")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    ap.add_argument("--room", default="7 栋 302")
    args = ap.parse_args()
    global BASE
    BASE = args.base.rstrip("/")

    now = datetime.now()
    tomorrow = now + timedelta(days=1)
    # 演示安排在「明天」：一张故意设在今天早上 8 点 —— 已经超时，卡片标红
    late = fmt(now.replace(hour=8, minute=0, second=0, microsecond=0))
    soon = fmt(tomorrow.replace(hour=21, minute=0, second=0, microsecond=0))
    later = fmt((now + timedelta(days=2)).replace(hour=12, minute=0, second=0, microsecond=0))
    overdue = late

    who = {}

    # 1. 建房
    opener, jar = new_client()
    st, room = call(opener, "POST", "/api/rooms", {"name": args.room})
    assert st == 200, room
    code = room["code"]

    # 2. 四个人加入
    for nick in ("小明", "小红", "阿强", "老王"):
        o, j = (opener, jar) if nick == "小明" else new_client()
        st, me = call(o, "POST", "/api/rooms/join", {"code": code, "nickname": nick})
        assert st == 200, me
        who[nick] = (o, member_id(j))

    def post(nick, path, body=None):
        st, body = call(who[nick][0], "POST", path, body)
        assert st == 200, (path, body)
        return body

    def publish(nick, station, pickup=None, deadline=later, reward="一杯奶茶"):
        return post(nick, "/api/parcels", {
            "station": station, "pickupCode": pickup, "deadline": deadline, "reward": reward,
        })

    def flow(pid, *steps):
        """steps: ("take", "小红"), ("pick", "小红"), ("deliver", "小明")"""
        for action, nick in steps:
            post(nick, f"/api/parcels/{pid}/{action}")

    # 3. 小明发布 5 张（进行中 3 张 + 历史 2 张）
    p_soon = publish("小明", "菜鸟驿站", "8-3-1024", soon, "一杯奶茶")      # 待接，今晚到期
    p_late = publish("小明", "京东快递", "3-2-7788", overdue, "免费")        # 待接，已超时（标红）
    p_taken = publish("小明", "顺丰快递", "A12-4471", later, "一杯奶茶")     # → 已接
    p_picked = publish("小明", "多多驿站", "5-1-2266", later, "免费")        # → 已取
    p_done1 = publish("小明", "菜鸟驿站", "7-2-3388", later, "一杯奶茶")     # → 已送达（历史）
    p_done2 = publish("小明", "京东快递", "1-9-4411", later, "免费")         # → 已送达（历史）
    p_cancel = publish("小明", "顺丰快递", "B07-6633", later, "一杯奶茶")    # → 已取消（历史）

    # 4. 别人接单，把状态推到各种位置
    flow(p_taken["id"], ("take", "小红"))                                   # 已接
    flow(p_picked["id"], ("take", "阿强"), ("pick", "阿强"))                # 已取
    flow(p_done1["id"], ("take", "小红"), ("pick", "小红"), ("deliver", "小明"))
    flow(p_done2["id"], ("take", "阿强"), ("pick", "阿强"), ("deliver", "小明"))
    post("小明", f"/api/parcels/{p_cancel['id']}/cancel")                    # 已取消

    # 5. 老王发布 2 张，小明去接（让「我接的单」有内容）
    q_picked = publish("老王", "兔喜快递", "6-4-1902", later, "一杯奶茶")
    flow(q_picked["id"], ("take", "小明"), ("pick", "小明"))                # 我接的单 · 已取

    q_done = publish("老王", "邮政快递", "9-8-7710", later, "免费")
    flow(q_done["id"], ("take", "小明"), ("pick", "小明"), ("deliver", "老王"))  # 我接的单 · 已完成

    # 6. 汇总
    st, active = call(who["小明"][0], "GET", "/api/parcels")
    st2, history = call(who["小明"][0], "GET", "/api/parcels/history")
    st3, mine = call(who["小明"][0], "GET", "/api/parcels/mine")
    st4, taken = call(who["小明"][0], "GET", "/api/parcels/mine?asTaker=1")
    st5, me = call(who["小明"][0], "GET", "/api/me")

    print(json.dumps({
        "base": BASE,
        "code": code,
        "room": room["name"],
        "members": {n: mid for n, (_, mid) in who.items()},
        "tabCounts": {
            "进行中": len(active), "我发布的": len(mine),
            "我接的单": len(taken), "历史": len(history),
            "未读提醒": me["unreadCount"],
        },
        "statuses": {p["id"]: p["status"] for p in (active + history + taken)},
        "howto": f"浏览器打开 {BASE} → 房间码 {code} → 昵称「小明」→ 四个 tab 都有内容",
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
