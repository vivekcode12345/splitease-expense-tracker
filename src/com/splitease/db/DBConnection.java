package com.splitease.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single point of JDBC connectivity for the SplitEase application.
 *
 * Configuration:
 *   - URL:      jdbc:mysql://localhost:3306/split_tracker
 *   - Username: root
 *   - Password: read from the DB_PASSWORD environment variable.
 *               Set it before running, e.g.  export DB_PASSWORD=your_root_password
 *
 * The MySQL Connector/J driver is loaded explicitly via Class.forName so the
 * application works on plain java with the JAR on the classpath - no Spring,
 * no Maven, no container.
 */
public final class DBConnection {

    private static final String URL =
        "jdbc:mysql://localhost:3306/split_tracker?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "root";

    /** MySQL root password, supplied at runtime so credentials are not committed. */
    private static final String PASSWORD = System.getProperty(
        "DB_PASSWORD",
        System.getenv().getOrDefault("DB_PASSWORD", "")
    );

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                "MySQL JDBC Driver not found on classpath. Ensure lib/mysql-connector-j-*.jar is on -cp.\n"
                + e.getMessage());
        }
    }

    private DBConnection() {
        // utility class - prevent instantiation
    }

    /**
     * Obtains a fresh JDBC Connection to the split_tracker database.
     *
     * @return an open Connection (caller is responsible for closing it)
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
