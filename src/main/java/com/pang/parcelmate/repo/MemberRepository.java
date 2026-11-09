package com.pang.parcelmate.repo;

import com.pang.parcelmate.domain.Member;
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

/** member 表的数据访问。 */
@Repository
public class MemberRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<Member> ROW_MAPPER = (rs, rowNum) -> new Member(
            rs.getLong("id"),
            rs.getLong("room_id"),
            rs.getString("nickname"),
            LocalDateTime.parse(rs.getString("join_at"), FMT),
            LocalDateTime.parse(rs.getString("last_active"), FMT)
    );

    private final JdbcTemplate jdbc;

    public MemberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long roomId, String nickname) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String now = LocalDateTime.now().format(FMT);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO member(room_id, nickname, join_at, last_active) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, roomId);
            ps.setString(2, nickname);
            ps.setString(3, now);
            ps.setString(4, now);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<Member> findById(long id) {
        List<Member> list = jdbc.query("SELECT * FROM member WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public List<Member> listByRoom(long roomId) {
        return jdbc.query("SELECT * FROM member WHERE room_id = ? ORDER BY id", ROW_MAPPER, roomId);
    }

    public boolean existsNickname(long roomId, String nickname) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member WHERE room_id = ? AND nickname = ?", Integer.class, roomId, nickname);
        return count != null && count > 0;
    }

    /** 记录活跃时间，用于"谁在线"。 */
    public void touch(long memberId) {
        jdbc.update("UPDATE member SET last_active = ? WHERE id = ?",
                LocalDateTime.now().format(FMT), memberId);
    }
}
