package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TelepassDebtRepository {
    private final DataSource ds;

    public record DebtRow(long id, String telepassId, long tripId, int amountCents, String currency, String status, Instant createdAt) {}

    public TelepassDebtRepository(DataSource ds) {
        this.ds = ds;
    }

    public void createDebt(String telepassId, long tripId, int amountCents, Instant createdAt) {
        String sql = """
                INSERT INTO telepass_debts(telepass_id, trip_id, amount_cents, status, created_at)
                VALUES (?, ?, ?, 'OPEN', ?)
                """;

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, telepassId);
            ps.setLong(2, tripId);
            ps.setInt(3, amountCents);
            ps.setObject(4, createdAt); // TIMESTAMPTZ

            int rows = ps.executeUpdate();
            if (rows != 1) {
                System.out.println("WARN createDebt affected rows=" + rows +
                        " telepassId=" + telepassId + " tripId=" + tripId);
            }
        } catch (SQLException e) {
            System.out.println("ERROR createDebt failed: " + e.getMessage());
            throw new IllegalStateException("DB error (createDebt)", e);
        }
    }

    public List<DebtRow> findByTelepassId(String telepassId) {
        String sql = """
                SELECT id, telepass_id, trip_id, amount_cents, currency, status, created_at
                FROM telepass_debts
                WHERE telepass_id = ?
                ORDER BY created_at DESC
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, telepassId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DebtRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new DebtRow(
                            rs.getLong("id"),
                            rs.getString("telepass_id"),
                            rs.getLong("trip_id"),
                            rs.getInt("amount_cents"),
                            rs.getString("currency"),
                            rs.getString("status"),
                            rs.getObject("created_at", Instant.class)
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (findByTelepassId)", e);
        }
    }

    public DebtRow findById(long debtId) {
        String sql = """
                SELECT id, telepass_id, trip_id, amount_cents, currency, status, created_at
                FROM telepass_debts
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, debtId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new DebtRow(
                        rs.getLong("id"),
                        rs.getString("telepass_id"),
                        rs.getLong("trip_id"),
                        rs.getInt("amount_cents"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getObject("created_at", Instant.class)
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (findById)", e);
        }
    }

    public Long markDebtPaid(long debtId) {
        String sql = """
                UPDATE telepass_debts
                SET status = 'PAID'
                WHERE id = ? AND status = 'OPEN'
                RETURNING trip_id
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, debtId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getLong("trip_id");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (markDebtPaid)", e);
        }
    }

    public long sumOpenDebtCents() {
        String sql = "SELECT COALESCE(SUM(amount_cents), 0) FROM telepass_debts WHERE status='OPEN'";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return 0L;
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("DB error (sumOpenDebtCents)", e);
        }
    }
}
