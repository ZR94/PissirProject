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

public class FaultRepository {
    private final DataSource ds;

    public record FaultRow(
            long id,
            String tollboothId,
            String direction,
            String channel,
            String code,
            String message,
            String severity,
            String status,
            String backendAction,
            Instant createdAt,
            Instant respondedAt
    ) {}

    public FaultRepository(DataSource ds) {
        this.ds = ds;
    }

    public long createFault(
            String tollboothId,
            String direction,
            String channel,
            String code,
            String message,
            String severity,
            Instant createdAt
    ) {
        String sql = """
                INSERT INTO device_faults(tollbooth_id, direction, channel, code, message, severity, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tollboothId);
            ps.setString(2, direction);
            ps.setString(3, channel);
            ps.setString(4, code);
            ps.setString(5, message);
            ps.setString(6, severity);
            ps.setTimestamp(7, Timestamp.from(createdAt));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("DB error (create fault missing id)");
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (create fault)", e);
        }
    }

    public List<FaultRow> findAll() {
        String sql = """
                SELECT id, tollbooth_id, direction, channel, code, message, severity, status, backend_action, created_at, responded_at
                FROM device_faults
                ORDER BY created_at DESC, id DESC
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<FaultRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRow(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (find faults)", e);
        }
    }

    public FaultRow findById(long id) {
        String sql = """
                SELECT id, tollbooth_id, direction, channel, code, message, severity, status, backend_action, created_at, responded_at
                FROM device_faults
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (find fault by id)", e);
        }
    }

    public boolean markResponded(long id, String backendAction, Instant respondedAt) {
        String sql = """
                UPDATE device_faults
                SET status = 'RESPONDED',
                    backend_action = ?,
                    responded_at = ?
                WHERE id = ? AND status = 'OPEN'
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, backendAction);
            ps.setTimestamp(2, Timestamp.from(respondedAt));
            ps.setLong(3, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (mark fault responded)", e);
        }
    }

    private static FaultRow mapRow(ResultSet rs) throws SQLException {
        return new FaultRow(
                rs.getLong("id"),
                rs.getString("tollbooth_id"),
                rs.getString("direction"),
                rs.getString("channel"),
                rs.getString("code"),
                rs.getString("message"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("backend_action"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("responded_at"))
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
