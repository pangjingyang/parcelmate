package com.pang.parcelmate.repo;

import com.pang.parcelmate.domain.ParcelEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** parcel_event 表：只追加、不修改的操作流水。 */
@Repository
public class ParcelEventRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<ParcelEvent> ROW_MAPPER = (rs, rowNum) -> {
        long byRaw = rs.getLong("by_member");
        Long by = rs.wasNull() ? null : byRaw;
        return new ParcelEvent(
                rs.getLong("id"),
                rs.getLong("parcel_id"),
                rs.getString("action"),
                by,
                LocalDateTime.parse(rs.getString("at"), FMT)
        );
    };

    private final JdbcTemplate jdbc;

    public ParcelEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(long parcelId, String action, Long byMember) {
        jdbc.update("INSERT INTO parcel_event(parcel_id, action, by_member, at) VALUES (?, ?, ?, ?)",
                parcelId, action, byMember, LocalDateTime.now().format(FMT));
    }

    public List<ParcelEvent> listByParcel(long parcelId) {
        return jdbc.query("SELECT * FROM parcel_event WHERE parcel_id = ? ORDER BY id", ROW_MAPPER, parcelId);
    }
}
