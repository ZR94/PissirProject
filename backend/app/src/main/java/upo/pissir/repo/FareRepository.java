package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FareRepository {
    private final DataSource ds;

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
}

