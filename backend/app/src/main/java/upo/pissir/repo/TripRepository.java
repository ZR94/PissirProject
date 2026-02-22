package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;

public class TripRepository {

    private final DataSource ds;

    public TripRepository(DataSource ds) {
        this.ds = ds;
    }

    public void createTripManual(String entryTollboothId, String plate, String ticketId, Instant entryAt) {
        String sql = """
            INSERT INTO trips(entry_tollbooth_id, ticket_id, plate, entry_at, currency, paid)
            VALUES (?, ?, ?, ?, 'EUR', false)
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entryTollboothId);
            ps.setString(2, ticketId);
            ps.setString(3, plate);
            ps.setTimestamp(4, Timestamp.from(entryAt));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("createTripManual failed", e);
        }
    }

    public void createTripTelepass(String entryTollboothId, String plate, String telepassId, Instant entryAt) {
        String sql = """
            INSERT INTO trips(entry_tollbooth_id, telepass_id, plate, entry_at, currency, paid)
            VALUES (?, ?, ?, ?, 'EUR', false)
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entryTollboothId);
            ps.setString(2, telepassId);
            ps.setString(3, plate);
            ps.setTimestamp(4, Timestamp.from(entryAt));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("createTripTelepass failed", e);
        }
    }

    public Long findActiveTripIdByTicket(String ticketId) {
        String sql = "SELECT id FROM trips WHERE ticket_id = ? AND exit_at IS NULL ORDER BY entry_at DESC LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findActiveTripIdByTicket failed", e);
        }
    }

    public Long findActiveTripIdByTelepass(String telepassId) {
        String sql = "SELECT id FROM trips WHERE telepass_id = ? AND exit_at IS NULL ORDER BY entry_at DESC LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, telepassId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findActiveTripIdByTelepass failed", e);
        }
    }

    public void closeTrip(long tripId, String exitTollboothId, Instant exitAt, int amountCents, boolean paid) {
        String sql = """
            UPDATE trips
            SET exit_tollbooth_id = ?,
                exit_at = ?,
                amount_cents = ?,
                currency = 'EUR',
                paid = ?
            WHERE id = ?
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, exitTollboothId);
            ps.setTimestamp(2, Timestamp.from(exitAt));
            ps.setInt(3, amountCents);
            ps.setBoolean(4, paid);
            ps.setLong(5, tripId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("closeTrip failed", e);
        }
    }

    public boolean markTripPaid(long tripId) {
        String sql = "UPDATE trips SET paid = true WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, tripId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("markTripPaid failed", e);
        }
    }

    public long sumCollectedCents() {
        String sql = "SELECT COALESCE(SUM(amount_cents), 0) FROM trips WHERE paid = true AND amount_cents IS NOT NULL";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return 0L;
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("sumCollectedCents failed", e);
        }
    }
}
