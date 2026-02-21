package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

public class TelepassDebtRepository {
    private final DataSource ds;

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
}
