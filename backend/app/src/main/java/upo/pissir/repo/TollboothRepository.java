package upo.pissir.repo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TollboothRepository {
    private final DataSource ds;

    public record TollboothRow(
            String id,
            String roadCode,
            Double kmMarker,
            String region,
            String description
    ) {}

    public TollboothRepository(DataSource ds) { this.ds = ds; }

    public List<TollboothRow> findAll() {
        String sql = """
                SELECT id, road_code, km_marker, region, description
                FROM tollbooths
                ORDER BY id
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<TollboothRow> out = new ArrayList<>();
            while (rs.next()) out.add(mapRow(rs));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("DB error (findAll tollbooths)", e);
        }
    }

    public TollboothRow findById(String id) {
        String sql = """
                SELECT id, road_code, km_marker, region, description
                FROM tollbooths
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (Exception e) {
            throw new IllegalStateException("DB error (findById tollbooth)", e);
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

    public void create(String id, String roadCode, double kmMarker, String region, String description) {
        String sql = """
                INSERT INTO tollbooths(id, road_code, km_marker, region, description)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, roadCode);
            ps.setDouble(3, kmMarker);
            ps.setString(4, region);
            ps.setString(5, description);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("DB error (create tollbooth)", e);
        }
    }

    public boolean update(String id, String roadCode, double kmMarker, String region, String description) {
        String sql = """
                UPDATE tollbooths
                SET road_code = ?, km_marker = ?, region = ?, description = ?
                WHERE id = ?
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, roadCode);
            ps.setDouble(2, kmMarker);
            ps.setString(3, region);
            ps.setString(4, description);
            ps.setString(5, id);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            throw new IllegalStateException("DB error (update tollbooth)", e);
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM tollbooths WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            throw new IllegalStateException("DB error (delete tollbooth)", e);
        }
    }

    private static TollboothRow mapRow(ResultSet rs) throws SQLException {
        return new TollboothRow(
                rs.getString("id"),
                rs.getString("road_code"),
                rs.getObject("km_marker", Double.class),
                rs.getString("region"),
                rs.getString("description")
        );
    }
}
