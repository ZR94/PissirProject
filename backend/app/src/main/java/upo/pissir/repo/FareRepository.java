package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class FareRepository {
    private final DataSource ds;

    public record FareRow(long id, String entryTollboothId, String exitTollboothId, int amountCents, String currency) {}

    public FareRepository(DataSource ds) {
        this.ds = ds;
    }

    public Integer findFareCents(String entryTollboothId, String exitTollboothId) {
        String sql = "SELECT amount_cents FROM fares WHERE entry_tollbooth_id=? AND exit_tollbooth_id=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entryTollboothId);
            ps.setString(2, exitTollboothId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt("amount_cents");
            }
        } catch (Exception e) {
            throw new IllegalStateException("DB error (findFareCents)", e);
        }
    }

    public List<FareRow> findAll() {
        String sql = """
                SELECT id, entry_tollbooth_id, exit_tollbooth_id, amount_cents, currency
                FROM fares
                ORDER BY entry_tollbooth_id, exit_tollbooth_id
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<FareRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new FareRow(
                        rs.getLong("id"),
                        rs.getString("entry_tollbooth_id"),
                        rs.getString("exit_tollbooth_id"),
                        rs.getInt("amount_cents"),
                        rs.getString("currency")
                ));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("DB error (findAll fares)", e);
        }
    }

    public long createFare(String entry, String exit, int cents) {
        String sql = """
                INSERT INTO fares(entry_tollbooth_id, exit_tollbooth_id, amount_cents, currency)
                VALUES (?, ?, ?, 'EUR')
                RETURNING id
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entry);
            ps.setString(2, exit);
            ps.setInt(3, cents);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("DB error (createFare) no id returned");
                }
                return rs.getLong("id");
            }
        } catch (Exception e) {
            throw new IllegalStateException("DB error (createFare)", e);
        }
    }

    public boolean updateFare(long id, int amountCents) {
        String sql = "UPDATE fares SET amount_cents = ? WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, amountCents);
            ps.setLong(2, id);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            throw new IllegalStateException("DB error (updateFare)", e);
        }
    }

    public boolean deleteFare(long id) {
        String sql = "DELETE FROM fares WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            throw new IllegalStateException("DB error (deleteFare)", e);
        }
    }
}
