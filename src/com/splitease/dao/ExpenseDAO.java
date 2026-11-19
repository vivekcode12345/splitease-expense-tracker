package com.splitease.dao;

import com.splitease.db.DBConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JDBC data access layer for SplitEase.
 *
 * Every method uses {@link PreparedStatement} (never string concatenation in SQL)
 * and opens/closes its own connection via {@link DBConnection}. Resources are
 * released with try-with-resources.
 */
public class ExpenseDAO {

    /* ------------------------- Users ------------------------- */

    public List<User> getAllUsers() throws SQLException {
        String sql = "SELECT user_id, name, email FROM users ORDER BY name";
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new User(
                    rs.getInt("user_id"),
                    rs.getString("name"),
                    rs.getString("email")
                ));
            }
        }
        return users;
    }

    public int addUser(String name, String email) throws SQLException {
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        }
    }

    /* --------------------- Expenses & Splits --------------------- */

    /**
     * Inserts a new expense and creates an equal split for every current user
     * inside a single JDBC transaction.
     *
     * @return the generated expense_id
     */
    public int addExpenseWithEqualSplit(String description, double totalAmount,
                                        int paidBy, String category) throws SQLException {
        String insertExpense =
            "INSERT INTO expenses (description, total_amount, paid_by, category) VALUES (?, ?, ?, ?)";
        String selectUsers = "SELECT user_id FROM users";
        String insertSplit =
            "INSERT INTO expense_splits (expense_id, user_id, owed_amount, is_settled) VALUES (?, ?, ?, 0)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int expenseId;
                try (PreparedStatement ps = conn.prepareStatement(insertExpense, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, description);
                    ps.setBigDecimal(2, BigDecimal.valueOf(totalAmount));
                    ps.setInt(3, paidBy);
                    ps.setString(4, category);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Creating expense failed, no generated key returned.");
                        }
                        expenseId = keys.getInt(1);
                    }
                }

                List<Integer> userIds = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectUsers);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        userIds.add(rs.getInt("user_id"));
                    }
                }
                if (userIds.isEmpty()) {
                    conn.rollback();
                    throw new SQLException("Cannot split expense: no users registered in the system.");
                }

                double share = totalAmount / userIds.size();
                try (PreparedStatement ps = conn.prepareStatement(insertSplit)) {
                    for (int userId : userIds) {
                        ps.setInt(1, expenseId);
                        ps.setInt(2, userId);
                        ps.setBigDecimal(3, BigDecimal.valueOf(share).setScale(2, RoundingMode.HALF_UP));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                return expenseId;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            }
        }
    }
    public List<Expense> getAllExpenses() throws SQLException {
        String sql = """
            SELECT e.expense_id, e.description, e.total_amount, e.paid_by,
                   u.name AS payer_name, e.category, e.created_at
            FROM expenses e
            JOIN users u ON e.paid_by = u.user_id
            ORDER BY e.created_at DESC
            """;
        List<Expense> expenses = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                expenses.add(new Expense(
                    rs.getInt("expense_id"),
                    rs.getString("description"),
                    rs.getDouble("total_amount"),
                    rs.getInt("paid_by"),
                    rs.getString("payer_name"),
                    rs.getString("category"),
                    rs.getTimestamp("created_at")
                ));
            }
        }
        return expenses;
    }

    /* ----------------------- Balances ----------------------- */
    /**
     * Net balance per user: total amount they paid minus total amount they owe.
     * Positive = others owe this user (they get money back).
     * Negative = this user owes others.
     *
     * Uses JOIN + SUM aggregates over expenses and expense_splits.
     */
    public List<UserBalance> getUserBalances() throws SQLException {
        String sql = """
            SELECT u.user_id, u.name,
                   COALESCE(p.total_paid, 0) AS total_paid,
                   COALESCE(s.total_owed, 0) AS total_owed,
                   (COALESCE(p.total_paid, 0) - COALESCE(s.total_owed, 0)) AS net_balance
            FROM users u
            LEFT JOIN (
                SELECT paid_by, SUM(total_amount) AS total_paid
                FROM expenses
                GROUP BY paid_by
            ) p ON p.paid_by = u.user_id
            LEFT JOIN (
                SELECT user_id, SUM(owed_amount) AS total_owed
                FROM expense_splits
                WHERE is_settled = 0
                GROUP BY user_id
            ) s ON s.user_id = u.user_id
            ORDER BY net_balance DESC
            """;
        List<UserBalance> balances = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                balances.add(new UserBalance(
                    rs.getInt("user_id"),
                    rs.getString("name"),
                    rs.getDouble("total_paid"),
                    rs.getDouble("total_owed"),
                    rs.getDouble("net_balance")
                ));
            }
        }
        return balances;
    }

    public int settleUserBalance(int userId) throws SQLException {
        String sql = "UPDATE expense_splits SET is_settled = 1 WHERE user_id = ? AND is_settled = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        }
    }
    /* ------------------ Category summary (Chart.js) ------------------ */

    public List<CategorySummary> getCategorySummary() throws SQLException {
        String sql = """
            SELECT category, SUM(total_amount) AS total
            FROM expenses
            GROUP BY category
            ORDER BY total DESC
            """;
        List<CategorySummary> summary = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                summary.add(new CategorySummary(
                    rs.getString("category"),
                    rs.getDouble("total")
                ));
            }
        }
        return summary;
    }

    public int pendingSettlementCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM expense_splits WHERE is_settled = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("cnt");
        }
    }

    public double totalGroupSpend() throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) AS total FROM expenses";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getDouble("total");
        }
    }

}
