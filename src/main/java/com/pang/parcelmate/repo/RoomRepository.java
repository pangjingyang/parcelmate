package com.pang.parcelmate.repo;

import com.pang.parcelmate.domain.Room;
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
 * room 表的数据访问。
 *
 * <p>这里只做「拼 SQL + 转对象」，业务判断一律放在 Service 层。
 */
@Repository
public class RoomRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<Room> ROW_MAPPER = (rs, rowNum) -> new Room(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            LocalDateTime.parse(rs.getString("created_at"), FMT)
    );

    private final JdbcTemplate jdbc;

    public RoomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 新建宿舍，返回自增主键。 */
    public long insert(String code, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO room(code, name, created_at) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, LocalDateTime.now().format(FMT));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<Room> findById(long id) {
        List<Room> list = jdbc.query("SELECT * FROM room WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Room> findByCode(String code) {
        List<Room> list = jdbc.query("SELECT * FROM room WHERE code = ?", ROW_MAPPER, code);
        return list.stream().findFirst();
    }

    public boolean existsCode(String code) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM room WHERE code = ?", Integer.class, code);
        return count != null && count > 0;
    }

    public int countAll() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM room", Integer.class);
        return count == null ? 0 : count;
    }
}
