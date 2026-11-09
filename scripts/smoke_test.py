"""Smoke test ParcelMate API end to end using cookie jar."""
import json
import urllib.request
import urllib.error
import http.cookiejar

BASE = "http://127.0.0.1:8080"


def client():
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    return opener


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


def main():
    # 1) 新访客
    guest = client()
    st, me = call(guest, "GET", "/api/me")
    print("1 guest /api/me ->", st, me)
    assert st == 200 and me["joined"] is False

    # 2) 建房
    st, room = call(guest, "POST", "/api/rooms", {"name": "7栋302"})
    print("2 create room ->", st, room)
    assert st == 200 and len(room["code"]) == 6
    code = room["code"]

    # 3) 小明加入
    xiaoming = client()
    st, me = call(xiaoming, "POST", "/api/rooms/join", {"code": code, "nickname": "小明"})
    print("3 join xiaoming ->", st, me["nickname"], "members:", [m["nickname"] for m in me["members"]])
    assert st == 200 and me["nickname"] == "小明"

    # 4) 小红加入
    xiaohong = client()
    st, me2 = call(xiaohong, "POST", "/api/rooms/join", {"code": code, "nickname": "小红"})
    print("4 join xiaohong ->", st, me2["nickname"], "members:", [m["nickname"] for m in me2["members"]])
    assert st == 200

    # 5) 重名被拦
    dup = client()
    st, err = call(dup, "POST", "/api/rooms/join", {"code": code, "nickname": "小明"})
    print("5 duplicate nickname ->", st, err)
    assert st == 400

    # 6) 错误房间码被拦
    st, err = call(dup, "POST", "/api/rooms/join", {"code": "ZZZZZZ", "nickname": "路人"})
    print("6 bad code ->", st, err)
    assert st == 400

    # 7) 发布快递
    st, p = call(xiaoming, "POST", "/api/parcels", {
        "courier": "中通",
        "station": "菜鸟驿站",
        "pickupCode": "8-3-1024",
        "deadline": "2026-11-09T20:30",
        "reward": "一杯奶茶",
    })
    print("7 create parcel ->", st, {k: p[k] for k in ("id", "courier", "status", "statusLabel", "pickupCode")})
    assert st == 200 and p["status"] == "OPEN" and p["pickupCode"] == "8-3-1024"

    # 8) 别人看列表：取件码必须是 null
    st, lst = call(xiaohong, "GET", "/api/parcels")
    print("8 xiaohong list ->", st, [(x["id"], x["pickupCode"], x["ownerName"]) for x in lst])
    assert st == 200 and lst[0]["pickupCode"] is None

    # 9) 发布者自己看列表：取件码可见
    st, lst = call(xiaoming, "GET", "/api/parcels")
    print("9 xiaoming list ->", st, [(x["id"], x["pickupCode"]) for x in lst])
    assert lst[0]["pickupCode"] == "8-3-1024"

    # 10) 历史 & 我的
    st, hist = call(xiaoming, "GET", "/api/parcels/history")
    print("10 history ->", st, len(hist))
    st, mine = call(xiaoming, "GET", "/api/parcels/mine?asTaker=false")
    print("11 mine ->", st, len(mine))
    st, taken = call(xiaohong, "GET", "/api/parcels/mine?asTaker=true")
    print("12 taken ->", st, len(taken))

    # 13) W2 接口当前应返回 501
    st, err = call(xiaohong, "POST", f"/api/parcels/{p['id']}/take")
    print("13 take (expect 501) ->", st, err)
    assert st == 501

    # 14) 未加入者发快递 -> 400
    st, err = call(guest, "POST", "/api/parcels", {"courier": "中通", "station": "菜鸟驿站"})
    print("14 guest create parcel ->", st, err)
    assert st == 400

    print("\nALL OK")


if __name__ == "__main__":
    main()
