package com.gallery.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lazy HikariCP DataSource configured from {@code db.properties} on the
 * classpath. System properties ({@code db.url}, {@code db.username},
 * {@code db.password}) and environment variables ({@code GALLERY_DB_URL},
 * {@code GALLERY_DB_USERNAME}, {@code GALLERY_DB_PASSWORD}) may override the
 * file, which is handy for CI and containers.
 */
public final class DbUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DbUtil.class);
    private static final String CONFIG_FILE = "db.properties";

    private static volatile HikariDataSource dataSource;

    private DbUtil() {
    }

    public static DataSource getDataSource() {
        HikariDataSource local = dataSource;
        if (local == null) {
            synchronized (DbUtil.class) {
                local = dataSource;
                if (local == null) {
                    local = createDataSource();
                    dataSource = local;
                }
            }
        }
        return local;
    }

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public static void close() {
        HikariDataSource local = dataSource;
        if (local != null) {
            local.close();
            dataSource = null;
        }
    }

    private static HikariDataSource createDataSource() {
        Properties properties = loadProperties();
        String url = resolve(properties, "db.url", "GALLERY_DB_URL");
        String username = resolve(properties, "db.username", "GALLERY_DB_USERNAME");
        String password = resolve(properties, "db.password", "GALLERY_DB_PASSWORD");
        String driverClass = properties.getProperty("db.driver", "oracle.jdbc.OracleDriver").trim();
        if (url == null || username == null || password == null) {
            throw new IllegalStateException(
                    "Database configuration is incomplete. Copy db.properties.example to "
                            + "src/main/resources/db.properties and set db.url, db.username and db.password.");
        }
        try {
            // Explicit loading makes the driver visible to DriverManager inside a
            // servlet container, where the webapp classloader may be initialised
            // after DriverManager's service scan.
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver not found on the classpath: " + driverClass, e);
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClass);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("gallery-pool");
        config.setMaximumPoolSize(intSetting(properties, "db.pool.maximumPoolSize", 10));
        config.setMinimumIdle(intSetting(properties, "db.pool.minimumIdle", 2));
        config.setConnectionTimeout(intSetting(properties, "db.pool.connectionTimeoutMs", 10000));
        LOG.info("Creating database pool for {}", url);
        return new HikariDataSource(config);
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream in = DbUtil.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new IllegalStateException(
                        CONFIG_FILE + " was not found on the classpath. Copy db.properties.example to "
                                + "src/main/resources/" + CONFIG_FILE + " and fill in your Oracle credentials.");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + CONFIG_FILE, e);
        }
        return properties;
    }

    private static String resolve(Properties properties, String key, String envName) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String fileValue = properties.getProperty(key);
        return fileValue == null || fileValue.isBlank() ? null : fileValue.trim();
    }

    private static int intSetting(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Ignoring invalid value for {}: {}", key, value);
            return fallback;
        }
    }
}
