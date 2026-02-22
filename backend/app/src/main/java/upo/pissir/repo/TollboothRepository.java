package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class TollboothRepository {
    private final DataSource ds;

    public TollboothRepository(DataSource ds) { this.ds = ds; }

    public List<String> findAll() {
        String sql = "SELECT id FROM tollbooths ORDER BY id";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<String> out = new ArrayList<>();
            while (rs.next()) out.add(rs.getString("id"));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("DB error (findAll tollbooths)", e);
        }
    }

    public boolean exists(String id) {
        String sql = "SELECT 1 FROM tollbooths WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("DB error (exists tollbooth)", e);
        }
    }

    public void create(String id) {
        String sql = "INSERT INTO tollbooths(id) VALUES (?)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("DB error (create tollbooth)", e);
        }
    }
}
