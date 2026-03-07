package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DeviceRepository {
    private final DataSource ds;

    public record DeviceRow(
            long id,
            String tollboothId,
            String direction,
            String channel,
            boolean enabled,
            Instant createdAt
    ) {}

    public DeviceRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<DeviceRow> findAll(String tollboothId) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tollbooth_id, direction, channel, enabled, created_at
                FROM devices
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (tollboothId != null && !tollboothId.isBlank()) {
            sql.append(" AND tollbooth_id = ?");
            params.add(tollboothId);
        }
        sql.append(" ORDER BY tollbooth_id, direction, channel");

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<DeviceRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new DeviceRow(
                            rs.getLong("id"),
                            rs.getString("tollbooth_id"),
                            rs.getString("direction"),
                            rs.getString("channel"),
                            rs.getBoolean("enabled"),
                            toInstant(rs.getTimestamp("created_at"))
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (findAll devices)", e);
        }
    }

    public long create(String tollboothId, String direction, String channel, boolean enabled) {
        String sql = """
                INSERT INTO devices(tollbooth_id, direction, channel, enabled)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tollboothId);
            ps.setString(2, direction);
            ps.setString(3, channel);
            ps.setBoolean(4, enabled);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("DB error (create device missing id)");
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (create device)", e);
        }
    }

    public boolean updateEnabled(long id, boolean enabled) {
        String sql = "UPDATE devices SET enabled = ? WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setLong(2, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (update device enabled)", e);
        }
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM devices WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (delete device)", e);
        }
    }

    public void createMandatoryForTollbooth(String tollboothId) {
        createIfMissing(tollboothId, "entry", "manual");
        createIfMissing(tollboothId, "entry", "telepass");
        createIfMissing(tollboothId, "exit", "manual");
        createIfMissing(tollboothId, "exit", "telepass");
    }

    private void createIfMissing(String tollboothId, String direction, String channel) {
        String sql = """
                INSERT INTO devices(tollbooth_id, direction, channel, enabled)
                VALUES (?, ?, ?, true)
                ON CONFLICT (tollbooth_id, direction, channel) DO NOTHING
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tollboothId);
            ps.setString(2, direction);
            ps.setString(3, channel);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (create mandatory devices)", e);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
