"""造一份演示数据：一个宿舍 + 4 个成员 + 3 张快递单。

用法：python seed_demo.py
输出最后一行是 JSON：{"code": "...", "memberId": N, "parcels": [...]}
"""
import json
import urllib.request
import urllib.error
import http.cookiejar
import sys

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
        return e.code, raw


def member_ids(jar):
    for c in jar:
        if c.name == "pm_member":
            return int(c.value)
    return None


def main():
    owner, owner_jar = new_client()
    st, room = call(owner, "POST", "/api/rooms", {"name": "7 栋 302"})
    assert st == 200, room
    code = room["code"]

    members = []
    for nick in ("小明", "小红", "阿强", "老王"):
        if not members:
            c, jar = owner, owner_jar
        else:
            c, jar = new_client()
        st, me = call(c, "POST", "/api/rooms/join", {"code": code, "nickname": nick})
        assert st == 200, me
        members.append((nick, me, member_ids(jar)))

    # 用小明（第一个成员）发三张单子
    xiaoming = owner
    parcels = []
    payloads = [
        {"courier": "中通", "station": "菜鸟驿站", "pickupCode": "8-3-1024",
         "deadline": "2026-11-09T20:30", "reward": "一杯奶茶"},
        {"courier": "顺丰", "station": "丰巢快递柜", "pickupCode": "A12-4471",
         "deadline": "2026-11-10T12:00", "reward": "免费"},
        {"courier": "京东", "station": "京东快递", "pickupCode": None,
         "deadline": None, "reward": "一杯奶茶"},
    ]
    for p in payloads:
        st, created = call(xiaoming, "POST", "/api/parcels", p)
        assert st == 200, created
        parcels.append(created["id"])

    print(json.dumps({
        "code": code,
        "roomName": room["name"],
        "members": [{"nickname": n, "id": i} for n, _, i in members],
        "memberId": members[0][2],
        "parcels": parcels,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
