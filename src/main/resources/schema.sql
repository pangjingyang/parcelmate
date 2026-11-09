-- ParcelMate 建表脚本
-- 每次启动都会执行，所以统一用 CREATE TABLE IF NOT EXISTS（幂等：重复执行不报错）

CREATE TABLE IF NOT EXISTS room (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    code       TEXT NOT NULL UNIQUE,      -- 6 位房间码，室友凭它加入
    name       TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS member (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id     INTEGER NOT NULL REFERENCES room(id),
    nickname    TEXT NOT NULL,
    join_at     TEXT NOT NULL,
    last_active TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_member_room ON member(room_id);

CREATE TABLE IF NOT EXISTS parcel (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id     INTEGER NOT NULL REFERENCES room(id),
    owner_id    INTEGER NOT NULL REFERENCES member(id),   -- 发布者
    courier     TEXT NOT NULL,                            -- 快递公司
    station     TEXT NOT NULL,                            -- 取件点：菜鸟/丰巢/京东
    pickup_code TEXT,                                     -- 取件码（只给发布者+接单人看）
    reward      TEXT,                                     -- 悬赏：一杯奶茶 / 免费
    deadline    TEXT,                                     -- 期望取件时间
    status      TEXT NOT NULL DEFAULT 'OPEN',             -- OPEN/TAKEN/PICKED/DELIVERED/CANCELED/EXCEPTION
    version     INTEGER NOT NULL DEFAULT 0,               -- 乐观锁版本号
    taker_id    INTEGER REFERENCES member(id),            -- 接单人
    created_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parcel_room ON parcel(room_id);
CREATE INDEX IF NOT EXISTS idx_parcel_owner ON parcel(owner_id);
CREATE INDEX IF NOT EXISTS idx_parcel_taker ON parcel(taker_id);

CREATE TABLE IF NOT EXISTS parcel_event (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    parcel_id INTEGER NOT NULL REFERENCES parcel(id),
    action    TEXT NOT NULL,          -- CREATED / TAKEN / PICKED / DELIVERED / CANCELED / EXCEPTION
    by_member INTEGER REFERENCES member(id),
    at        TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_parcel ON parcel_event(parcel_id);

CREATE TABLE IF NOT EXISTS notice (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id INTEGER NOT NULL REFERENCES member(id),
    parcel_id INTEGER REFERENCES parcel(id),
    type      TEXT NOT NULL,          -- TAKEN / PICKED / DELIVERED / CANCELED（文案在 NoticeService 里翻译）
    is_read   INTEGER NOT NULL DEFAULT 0,
    at        TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notice_member ON notice(member_id, is_read);
