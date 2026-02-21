package upo.pissir.db;


import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

public final class SchemaInitializer {
    private SchemaInitializer() {}

    public static void init(DataSource ds) {
        String sql = readResource("/db/schema.sql");
        if (sql == null || sql.isBlank()) {
            throw new IllegalStateException("db/schema.sql not found or empty");
        }

        // Split semplice per ';' (ok per schema senza funzioni PL/pgSQL)
        String[] statements = sql
                .replace("\r\n", "\n")
                .split(";");
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                for (String raw : statements) {
                    String s = stripComments(raw).trim();
                    if (s.isBlank()) continue;
                    st.execute(s);
                }
            }
            c.commit();
        } catch (Exception e) {
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    private static String readResource(String path) {
        var is = SchemaInitializer.class.getResourceAsStream(path);
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource " + path, e);
        }
    }

    // Rimuove commenti '-- ...' per evitare statement sporchi
    private static String stripComments(String s) {
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n")) {
            int idx = line.indexOf("--");
            out.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return out.toString();
    }
}
