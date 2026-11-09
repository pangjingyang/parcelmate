package com.pang.parcelmate.repo;

import com.pang.parcelmate.domain.Notice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** notice 表：站内提醒（红点）。W2 接单/送达时会给相关人写一条。 */
@Repository
public class NoticeRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<Notice> ROW_MAPPER = (rs, rowNum) -> {
        long parcelRaw = rs.getLong("parcel_id");
        Long parcelId = rs.wasNull() ? null : parcelRaw;
        return new Notice(
                rs.getLong("id"),
                rs.getLong("member_id"),
                parcelId,
                rs.getString("type"),
                rs.getInt("is_read") == 1,
                LocalDateTime.parse(rs.getString("at"), FMT)
        );
    };

    private final JdbcTemplate jdbc;

    public NoticeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long memberId, Long parcelId, String type) {
        jdbc.update("INSERT INTO notice(member_id, parcel_id, type, is_read, at) VALUES (?, ?, ?, 0, ?)",
                memberId, parcelId, type, LocalDateTime.now().format(FMT));
    }

    public List<Notice> listUnread(long memberId) {
        return jdbc.query("SELECT * FROM notice WHERE member_id = ? AND is_read = 0 ORDER BY id DESC",
                ROW_MAPPER, memberId);
    }

    public int countUnread(long memberId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notice WHERE member_id = ? AND is_read = 0", Integer.class, memberId);
        return count == null ? 0 : count;
    }

    public void markAllRead(long memberId) {
        jdbc.update("UPDATE notice SET is_read = 1 WHERE member_id = ?", memberId);
    }
}
