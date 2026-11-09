package com.pang.parcelmate.repo;

import com.pang.parcelmate.domain.Parcel;
import com.pang.parcelmate.domain.ParcelStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * parcel 表的数据访问。
 *
 * <p>【本项目最重要的一段代码】{@link #takeIfOpen} 用「状态 + 版本号」做条件更新，
 * 让两个同时点"接单"的人里只有一个能成功。W2 会把这段 SQL 拆开细讲。
 */
@Repository
public class ParcelRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<Parcel> ROW_MAPPER = (rs, rowNum) -> {
        long takerRaw = rs.getLong("taker_id");
        Long takerId = rs.wasNull() ? null : takerRaw;
        String deadline = rs.getString("deadline");
        return new Parcel(
                rs.getLong("id"),
                rs.getLong("room_id"),
                rs.getLong("owner_id"),
                rs.getString("courier"),
                rs.getString("station"),
                rs.getString("pickup_code"),
                rs.getString("reward"),
                deadline == null ? null : LocalDateTime.parse(deadline, FMT),
                ParcelStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                takerId,
                LocalDateTime.parse(rs.getString("created_at"), FMT)
        );
    };

    private final JdbcTemplate jdbc;

    public ParcelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long roomId, long ownerId, String courier, String station,
                       String pickupCode, String reward, LocalDateTime deadline) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO parcel(room_id, owner_id, courier, station, pickup_code, reward,
                                       deadline, status, version, taker_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', 0, NULL, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, roomId);
            ps.setLong(2, ownerId);
            ps.setString(3, courier);
            ps.setString(4, station);
            ps.setString(5, pickupCode);
            ps.setString(6, reward);
            ps.setString(7, deadline == null ? null : deadline.format(FMT));
            ps.setString(8, LocalDateTime.now().format(FMT));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<Parcel> findById(long id) {
        List<Parcel> list = jdbc.query("SELECT * FROM parcel WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    /** 宿舍里所有还在进行中的单子（不含已结束），按发布时间倒序。 */
    public List<Parcel> listActiveByRoom(long roomId) {
        return jdbc.query("""
                SELECT * FROM parcel
                WHERE room_id = ? AND status IN ('OPEN','TAKEN','PICKED')
                ORDER BY id DESC
                """, ROW_MAPPER, roomId);
    }

    /** 宿舍里全部单子（含历史）。 */
    public List<Parcel> listAllByRoom(long roomId) {
        return jdbc.query("SELECT * FROM parcel WHERE room_id = ? ORDER BY id DESC", ROW_MAPPER, roomId);
    }

    public List<Parcel> listByOwner(long ownerId) {
        return jdbc.query("SELECT * FROM parcel WHERE owner_id = ? ORDER BY id DESC", ROW_MAPPER, ownerId);
    }

    public List<Parcel> listByTaker(long takerId) {
        return jdbc.query("SELECT * FROM parcel WHERE taker_id = ? ORDER BY id DESC", ROW_MAPPER, takerId);
    }

    /**
     * 【乐观锁】把 OPEN 的单子抢下来。
     *
     * <pre>
     * UPDATE parcel SET status='TAKEN', taker_id=?, version=version+1
     *  WHERE id=? AND status='OPEN' AND version=?
     * </pre>
     *
     * WHERE 里的 status 和 version 是两个"门闩"：
     * - 别人已经接走 → status 不再是 OPEN → 0 行受影响
     * - 你手里拿的是旧版本 → version 对不上 → 0 行受影响
     * 只有 1 行受影响，才说明"这一单确实是刚被我抢到的"。
     *
     * @return 受影响行数，1 = 抢到，0 = 被人抢先/状态已变
     */
    public int takeIfOpen(long parcelId, long expectedVersion, long takerId) {
        return jdbc.update("""
                UPDATE parcel SET status = 'TAKEN', taker_id = ?, version = version + 1
                 WHERE id = ? AND status = 'OPEN' AND version = ?
                """, takerId, parcelId, expectedVersion);
    }

    /**
     * 通用的状态流转（已接→已取→已送达，以及取消/异常）。
     * 同样带 version 校验，避免"两个人同时点已送达"重复记账。
     */
    public int transit(long parcelId, ParcelStatus from, ParcelStatus to, long expectedVersion) {
        return jdbc.update("""
                UPDATE parcel SET status = ?, version = version + 1
                 WHERE id = ? AND status = ? AND version = ?
                """, to.name(), parcelId, from.name(), expectedVersion);
    }
}
