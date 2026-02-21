package upo.pissir.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class Db {

    private Db() {
    }

    public static DataSource createDataSource() {
        // Priority:
        // 1) DB_URL if provided
        // 2) Build from DB_HOST/DB_PORT/DB_NAME
        String jdbcUrl = env("DB_URL", "");
        if (jdbcUrl.isBlank()) {
            String host = env("DB_HOST", "localhost");
            String port = env("DB_PORT", "5432");
            String name = env("DB_NAME", "pissirdb");
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + name;
        }

        String user = env("DB_USER", "admin");
        String pass = env("DB_PASSWORD", "PISSIR");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pass);

        // Minimal and robust pool settings
        cfg.setMaximumPoolSize(Integer.parseInt(env("DB_POOL_MAX", "10")));
        cfg.setMinimumIdle(Integer.parseInt(env("DB_POOL_MIN", "1")));
        cfg.setConnectionTimeout(Long.parseLong(env("DB_POOL_CONN_TIMEOUT_MS", "5000")));

        return new HikariDataSource(cfg);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
