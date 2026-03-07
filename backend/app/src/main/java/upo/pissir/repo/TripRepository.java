package upo.pissir.repo;

import upo.pissir.dto.TripResponse;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

public class TripRepository {

    private final DataSource ds;

    public record ActiveTripRow(long id, String entryTollboothId, String plate, Instant entryAt) {}
    public record ActiveTripReportRow(
            long id,
            String entryTollboothId,
            String ticketId,
            String telepassId,
            String plate,
            Instant entryAt
    ) {}
    public record RouteStatsRow(
            String entryTollboothId,
            String exitTollboothId,
            long tripsCount,
            long paidTripsCount,
            long unpaidTripsCount,
            long totalAmountCents,
            long avgAmountCents
    ) {}

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

    public ActiveTripRow findActiveTripByTicket(String ticketId) {
        String sql = """
                SELECT id, entry_tollbooth_id, plate, entry_at
                FROM trips
                WHERE ticket_id = ? AND exit_at IS NULL
                ORDER BY entry_at DESC
                LIMIT 1
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ActiveTripRow(
                        rs.getLong("id"),
                        rs.getString("entry_tollbooth_id"),
                        rs.getString("plate"),
                        toInstant(rs.getTimestamp("entry_at"))
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findActiveTripByTicket failed", e);
        }
    }

    public ActiveTripRow findActiveTripByTelepass(String telepassId) {
        String sql = """
                SELECT id, entry_tollbooth_id, plate, entry_at
                FROM trips
                WHERE telepass_id = ? AND exit_at IS NULL
                ORDER BY entry_at DESC
                LIMIT 1
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, telepassId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ActiveTripRow(
                        rs.getLong("id"),
                        rs.getString("entry_tollbooth_id"),
                        rs.getString("plate"),
                        toInstant(rs.getTimestamp("entry_at"))
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findActiveTripByTelepass failed", e);
        }
    }

    public void closeTrip(
            long tripId,
            String exitTollboothId,
            Instant exitAt,
            int amountCents,
            Double avgSpeedKmh,
            boolean speeding,
            boolean paid
    ) {
        String sql = """
            UPDATE trips
            SET exit_tollbooth_id = ?,
                exit_at = ?,
                amount_cents = ?,
                currency = 'EUR',
                avg_speed_kmh = ?,
                speeding = ?,
                paid = ?
            WHERE id = ?
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, exitTollboothId);
            ps.setTimestamp(2, Timestamp.from(exitAt));
            ps.setInt(3, amountCents);
            if (avgSpeedKmh == null) {
                ps.setNull(4, Types.NUMERIC);
            } else {
                ps.setDouble(4, avgSpeedKmh);
            }
            ps.setBoolean(5, speeding);
            ps.setBoolean(6, paid);
            ps.setLong(7, tripId);
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

    public List<TripResponse> findTripsForReport(
            Instant from,
            Instant to,
            String entryTollboothId,
            String exitTollboothId,
            String channel,
            Boolean paid,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, entry_tollbooth_id, exit_tollbooth_id, ticket_id, telepass_id, plate, entry_at, exit_at, amount_cents, currency, avg_speed_kmh, speeding, paid
                FROM trips
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        appendReportFilters(sql, params, from, to, entryTollboothId, exitTollboothId, channel, paid);
        sql.append(" ORDER BY entry_at DESC LIMIT ?");
        params.add(limit);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<TripResponse> out = new ArrayList<>();
                while (rs.next()) {
                    Instant entryAt = toInstant(rs.getTimestamp("entry_at"));
                    Instant exitAt = toInstant(rs.getTimestamp("exit_at"));
                    out.add(new TripResponse(
                            rs.getLong("id"),
                            rs.getString("entry_tollbooth_id"),
                            rs.getString("exit_tollbooth_id"),
                            rs.getString("ticket_id"),
                            rs.getString("telepass_id"),
                            rs.getString("plate"),
                            entryAt == null ? null : entryAt.toString(),
                            exitAt == null ? null : exitAt.toString(),
                            rs.getObject("amount_cents", Integer.class),
                            rs.getString("currency"),
                            rs.getObject("avg_speed_kmh", Double.class),
                            rs.getBoolean("speeding"),
                            rs.getBoolean("paid")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findTripsForReport failed", e);
        }
    }

    public List<RouteStatsRow> findRouteStatsForReport(
            Instant from,
            Instant to,
            String entryTollboothId,
            String exitTollboothId,
            String channel,
            Boolean paid
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    entry_tollbooth_id,
                    exit_tollbooth_id,
                    COUNT(*) AS trips_count,
                    SUM(CASE WHEN paid THEN 1 ELSE 0 END) AS paid_trips_count,
                    SUM(CASE WHEN NOT paid THEN 1 ELSE 0 END) AS unpaid_trips_count,
                    COALESCE(SUM(amount_cents), 0) AS total_amount_cents,
                    COALESCE(AVG(amount_cents)::bigint, 0) AS avg_amount_cents
                FROM trips
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        appendReportFilters(sql, params, from, to, entryTollboothId, exitTollboothId, channel, paid);
        sql.append(" AND exit_tollbooth_id IS NOT NULL");
        sql.append(" GROUP BY entry_tollbooth_id, exit_tollbooth_id");
        sql.append(" ORDER BY trips_count DESC, entry_tollbooth_id, exit_tollbooth_id");

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<RouteStatsRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new RouteStatsRow(
                            rs.getString("entry_tollbooth_id"),
                            rs.getString("exit_tollbooth_id"),
                            rs.getLong("trips_count"),
                            rs.getLong("paid_trips_count"),
                            rs.getLong("unpaid_trips_count"),
                            rs.getLong("total_amount_cents"),
                            rs.getLong("avg_amount_cents")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findRouteStatsForReport failed", e);
        }
    }

    public List<ActiveTripReportRow> findActiveTrips(
            String entryTollboothId,
            String channel,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, entry_tollbooth_id, ticket_id, telepass_id, plate, entry_at
                FROM trips
                WHERE exit_at IS NULL
                """);
        List<Object> params = new ArrayList<>();

        if (entryTollboothId != null && !entryTollboothId.isBlank()) {
            sql.append(" AND entry_tollbooth_id = ?");
            params.add(entryTollboothId);
        }
        if ("manual".equals(channel)) {
            sql.append(" AND ticket_id IS NOT NULL");
        } else if ("telepass".equals(channel)) {
            sql.append(" AND telepass_id IS NOT NULL");
        }

        sql.append(" ORDER BY entry_at DESC LIMIT ?");
        params.add(limit);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<ActiveTripReportRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new ActiveTripReportRow(
                            rs.getLong("id"),
                            rs.getString("entry_tollbooth_id"),
                            rs.getString("ticket_id"),
                            rs.getString("telepass_id"),
                            rs.getString("plate"),
                            toInstant(rs.getTimestamp("entry_at"))
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findActiveTrips failed", e);
        }
    }

    private static void appendReportFilters(
            StringBuilder sql,
            List<Object> params,
            Instant from,
            Instant to,
            String entryTollboothId,
            String exitTollboothId,
            String channel,
            Boolean paid
    ) {
        if (from != null) {
            sql.append(" AND entry_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND entry_at <= ?");
            params.add(Timestamp.from(to));
        }
        if (entryTollboothId != null && !entryTollboothId.isBlank()) {
            sql.append(" AND entry_tollbooth_id = ?");
            params.add(entryTollboothId);
        }
        if (exitTollboothId != null && !exitTollboothId.isBlank()) {
            sql.append(" AND exit_tollbooth_id = ?");
            params.add(exitTollboothId);
        }
        if ("manual".equals(channel)) {
            sql.append(" AND ticket_id IS NOT NULL");
        } else if ("telepass".equals(channel)) {
            sql.append(" AND telepass_id IS NOT NULL");
        }
        if (paid != null) {
            sql.append(" AND paid = ?");
            params.add(paid);
        }
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
