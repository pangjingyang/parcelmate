"""ParcelMate 接口冒烟测试（端到端，只用标准库）。

用法：先启动服务，再 `python smoke_test.py`；末行打印 ALL OK 即通过。

覆盖：身份 / 建房加入 / 隐私（取件码可见性）/ 发布 / W2 状态机流转 /
      越权拦截 / 站内提醒 / **并发的两人同时接单只有一个人成功**。
"""
import json
import threading
import urllib.request
import urllib.error
import http.cookiejar

BASE = "http://127.0.0.1:8080"


def client():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


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


def join(code, nickname):
    c = client()
    st, me = call(c, "POST", "/api/rooms/join", {"code": code, "nickname": nickname})
    assert st == 200, me
    return c, me


def main():
    # ---------- 身份与宿舍 ----------
    guest = client()
    st, me = call(guest, "GET", "/api/me")
    print("1  guest /api/me ->", st, "joined =", me["joined"])
    assert st == 200 and me["joined"] is False

    st, room = call(guest, "POST", "/api/rooms", {"name": "7栋302"})
    print("2  create room ->", st, room["code"])
    assert st == 200 and len(room["code"]) == 6
    code = room["code"]

    xiaoming, me_xm = join(code, "小明")
    print("3  join 小明 ->", me_xm["memberId"], "成员:", [m["nickname"] for m in me_xm["members"]])

    xiaohong, me_xh = join(code, "小红")
    print("4  join 小红 ->", me_xh["memberId"])

    dup = client()
    st, err = call(dup, "POST", "/api/rooms/join", {"code": code, "nickname": "小明"})
    print("5  重名 ->", st, err)
    assert st == 400

    st, err = call(dup, "POST", "/api/rooms/join", {"code": "ZZZZZZ", "nickname": "路人"})
    print("6  错房间码 ->", st, err)
    assert st == 400

    # ---------- 发布与隐私 ----------
    st, p = call(xiaoming, "POST", "/api/parcels", {
        "courier": "中通", "station": "菜鸟驿站", "pickupCode": "8-3-1024",
        "deadline": "2026-11-09T20:30", "reward": "一杯奶茶",
    })
    pid = p["id"]
    print("7  发布 ->", st, pid, p["status"], "取件码 =", p["pickupCode"])
    assert st == 200 and p["status"] == "OPEN"

    st, lst = call(xiaohong, "GET", "/api/parcels")
    print("8  他人看列表 -> 取件码 =", lst[0]["pickupCode"])
    assert lst[0]["pickupCode"] is None

    st, lst = call(xiaoming, "GET", "/api/parcels")
    print("9  发布者看列表 -> 取件码 =", lst[0]["pickupCode"])
    assert lst[0]["pickupCode"] == "8-3-1024"

    # ---------- W2 状态机 ----------
    st, err = call(xiaoming, "POST", f"/api/parcels/{pid}/take")
    print("10 自己接自己的单 ->", st, err)
    assert st == 400

    st, err = call(xiaohong, "POST", "/api/parcels/{}/deliver".format(pid))
    print("11 还没接就点送达 ->", st, err)
    assert st == 400

    st, taken = call(xiaohong, "POST", f"/api/parcels/{pid}/take")
    print("12 小红接单 ->", st, taken["status"], taken["takerName"], "取件码 =", taken["pickupCode"])
    assert st == 200 and taken["status"] == "TAKEN" and taken["pickupCode"] == "8-3-1024"

    stranger = client()
    st, err = call(stranger, "POST", f"/api/parcels/{pid}/take")
    print("13 未加入者接单 ->", st, err)
    assert st == 400

    st, err = call(xiaoming, "POST", f"/api/parcels/{pid}/take")
    print("14 发布者再抢已接走的单 ->", st, err)
    assert st == 400

    st, err = call(xiaohong, "POST", f"/api/parcels/{pid}/deliver")
    print("15 接单人点送达（应只有发布者能点）->", st, err)
    assert st == 400

    st, picked = call(xiaohong, "POST", f"/api/parcels/{pid}/pick")
    print("16 小红已取到 ->", st, picked["status"], picked["statusLabel"])
    assert st == 200 and picked["status"] == "PICKED"

    st, err = call(xiaohong, "POST", f"/api/parcels/{pid}/pick")
    print("17 重复点已取到 ->", st, err)
    assert st == 400

    st, done = call(xiaoming, "POST", f"/api/parcels/{pid}/deliver")
    print("18 小明确认已收到 ->", st, done["status"], done["statusLabel"])
    assert st == 200 and done["status"] == "DELIVERED"

    # ---------- 站内提醒 ----------
    st, notices = call(xiaoming, "GET", "/api/notices")
    print("19 小明的消息 ->", st, [n["text"] for n in notices])
    assert st == 200 and len(notices) >= 2

    st, _ = call(xiaoming, "POST", "/api/notices/read")
    st, notices = call(xiaoming, "GET", "/api/notices")
    print("20 标记已读后 ->", len(notices), "条未读")
    assert len(notices) == 0

    # ---------- 取消 ----------
    st, p2 = call(xiaoming, "POST", "/api/parcels", {
        "courier": "顺丰", "station": "丰巢快递柜", "pickupCode": "A12-4471",
    })
    pid2 = p2["id"]
    st, err = call(xiaohong, "POST", f"/api/parcels/{pid2}/cancel")
    print("21 别人取消我的单 ->", st, err)
    assert st == 400

    st, canceled = call(xiaoming, "POST", f"/api/parcels/{pid2}/cancel")
    print("22 发布者取消 ->", st, canceled["status"], canceled["statusLabel"])
    assert st == 200 and canceled["status"] == "CANCELED"

    # ---------- 并发：5 个人同时抢同一张单 ----------
    st, p3 = call(xiaoming, "POST", "/api/parcels", {
        "courier": "圆通", "station": "菜鸟驿站", "pickupCode": "9-9-9999",
    })
    pid3 = p3["id"]
    racers = [join(code, f"室友{i}")[0] for i in range(1, 6)]

    barrier = threading.Barrier(len(racers))
    results = []
    lock = threading.Lock()

    def race(c, idx):
        barrier.wait()
        st, body = call(c, "POST", f"/api/parcels/{pid3}/take")
        with lock:
            results.append((idx, st, body))

    threads = [threading.Thread(target=race, args=(c, i)) for i, c in enumerate(racers)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    ok = [r for r in results if r[1] == 200]
    fail = [r for r in results if r[1] != 200]
    print("23 并发 5 人抢单 -> 成功", len(ok), "人，失败", len(fail), "人")
    assert len(ok) == 1, results

    st, lst = call(xiaoming, "GET", "/api/parcels/history")
    final = [x for x in lst if x["id"] == pid3][0]
    print("24 最终状态 ->", final["status"], "接单人 =", final["takerName"], "version =", final["version"])
    assert final["version"] == 1

    # ---------- 未加入者 ----------
    st, err = call(guest, "POST", "/api/parcels", {"courier": "中通", "station": "菜鸟驿站"})
    print("25 未加入者发快递 ->", st, err)
    assert st == 400

    print("\nALL OK")


if __name__ == "__main__":
    main()
