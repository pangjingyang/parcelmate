"""造一份演示数据：一个宿舍 + 4 个成员 + 3 张快递单（其中 1 张已被接走）。

用法：
    python seed_demo.py                       # 默认打本机 8080
    python seed_demo.py --base http://1.2.3.4:8080
    python seed_demo.py --base ... --clean    # 先提示清库（服务器上需手动停服删 data/）

最后一行输出 JSON：{"code": "ABC123", "members": [...], "parcels": [...]}
把 code 念给室友，他们在手机上输房间码 + 昵称就能进。
"""
import argparse
import http.cookiejar
import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"


def new_client():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar)), jar


def call(opener, method, path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    try:
        with opener.open(req, timeout=20) as r:
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080", help="服务地址")
    args = ap.parse_args()
    global BASE
    BASE = args.base.rstrip("/")

    students = {}
    clients = {}

    # 1. 建房（建房本身不写 cookie，前端建完房会再 join 一次）
    opener, jar = new_client()
    st, room = call(opener, "POST", "/api/rooms", {"name": "7 栋 302"})
    assert st == 200, room
    code = room["code"]

    # 2. 四位室友依次加入（小明复用建房那个连接）
    for nick in ("小明", "小红", "阿强", "老王"):
        if nick == "小明":
            o, j = opener, jar
        else:
            o, j = new_client()
        st, me = call(o, "POST", "/api/rooms/join", {"code": code, "nickname": nick})
        assert st == 200, me
        students[nick] = (o, member_id(j))

    # 3. 小明发三张单
    payloads = [
        {"courier": "中通", "station": "菜鸟驿站", "pickupCode": "8-3-1024",
         "deadline": "2026-11-11T20:30", "reward": "一杯奶茶"},
        {"courier": "顺丰", "station": "丰巢快递柜", "pickupCode": "A12-4471",
         "deadline": "2026-11-11T12:00", "reward": "免费"},
        {"courier": "京东", "station": "京东快递", "pickupCode": None,
         "deadline": None, "reward": "一杯奶茶"},
    ]
    parcels = []
    for p in payloads:
        st, created = call(students["小明"][0], "POST", "/api/parcels", p)
        assert st == 200, created
        parcels.append(created)

    # 4. 小红接走顺丰那单 —— 演示时首屏就能看到「待接」和「已接」两种状态
    st, taken = call(students["小红"][0], "POST", f"/api/parcels/{parcels[1]['id']}/take")
    assert st == 200, taken

    print(json.dumps({
        "base": BASE,
        "code": code,
        "roomName": room["name"],
        "members": [{"nickname": n, "id": mid} for n, (_, mid) in students.items()],
        "parcels": [
            {"id": p["id"], "courier": p["courier"], "status": "TAKEN" if p["id"] == parcels[1]["id"] else "OPEN"}
            for p in parcels
        ],
        "next": "手机打开 %s → 输房间码 %s → 昵称随便（如「学长」）→ 就能看到 2 张待接 + 1 张已接" % (BASE, code),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
