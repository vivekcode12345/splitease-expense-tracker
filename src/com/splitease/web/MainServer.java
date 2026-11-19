package com.splitease.web;

import com.splitease.dao.CategorySummary;
import com.splitease.dao.Expense;
import com.splitease.dao.ExpenseDAO;
import com.splitease.dao.User;
import com.splitease.dao.UserBalance;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * SplitEase web server.
 *
 * A single-file, dependency-free (beyond the MySQL JDBC driver) HTTP server built
 * on the JDK's {@link com.sun.net.httpserver.HttpServer}. All pages are rendered
 * server-side as HTML with Tailwind CSS (CDN) and Chart.js (CDN).
 *
 * Endpoints:
 *   GET  /             - dashboard
 *   POST /add-user     - add a member (redirects to /)
 *   POST /add-expense  - add expense + equal splits (redirects to /)
 *   GET  /settle       - settle a user's pending splits (redirects to /)
 */
public class MainServer {

    private static final int PORT = 8080;
    private static final ExpenseDAO dao = new ExpenseDAO();
    /** Currency formatter: ₹1,234.56 */

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/", new DashboardHandler());
        server.createContext("/add-user", new AddUserHandler());
        server.createContext("/add-expense", new AddExpenseHandler());
        server.createContext("/settle", new SettleHandler());

        server.start();
        System.out.println("SplitEase server running on http://localhost:" + PORT);
        System.out.println("Press Ctrl+C to stop.");
    }

    /* ------------------------- Utilities ------------------------- */

    static String money(double amount) {
        return "\u20B9" + String.format("%,.2f", amount);
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Parses application/x-www-form-urlencoded request body into a map. */
    static Map<String, String> parseForm(InputStream body) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[1024];
        int read;
        while ((read = body.read(buf)) != -1) {
            sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
        }
        Map<String, String> form = new HashMap<>();
        for (String pair : sb.toString().split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                form.put(urlDecode(pair.substring(0, idx)), urlDecode(pair.substring(idx + 1)));
            }
        }
        return form;
    }

    static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.getResponseBody().close();
    }

    static final class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendRedirect(exchange, "/");
                return;
            }
            try {
                String error = null;
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        switch (param) {
                            case "error=add_user_failed"    -> error = "Failed to add member.";
                            case "error=add_expense_failed" -> error = "Failed to add expense.";
                            case "error=settle_failed"      -> error = "Failed to settle balance.";
                        }
                    }
                }
                sendHtml(exchange, buildDashboard(error));
            } catch (SQLException e) {
                String error = "<h1 class='p-6 text-red-600'>Database error</h1><pre class='p-4 bg-red-50 border border-red-200'>"
                        + escape(e.getMessage()) + "</pre>";
                sendHtml(exchange, error);
                e.printStackTrace();
            }
        }
    }

    /**
     * Server-side renders the full dashboard HTML. Data is fetched fresh from the
     * DAO on every request, so the UI always reflects the latest DB state.
     */
    static String buildDashboard(String error) throws SQLException {
        List<User> users = dao.getAllUsers();
        List<Expense> expenses = dao.getAllExpenses();
        List<UserBalance> balances = dao.getUserBalances();
        List<CategorySummary> categories = dao.getCategorySummary();

        double totalSpend = dao.totalGroupSpend();
        int pendingCount = dao.pendingSettlementCount();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>SplitEase - Expense & Split-Bill Tracker</title>\n");
        html.append("  <script src=\"https://cdn.tailwindcss.com\"></script>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n");
        html.append("  <link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&amp;family=JetBrains+Mono:wght@400;700&amp;display=swap\" rel=\"stylesheet\">\n");
        html.append("  <style>\n");
        html.append("    body { font-family: 'Plus Jakarta Sans', ui-sans-serif, system-ui, sans-serif; }\n");
        html.append("    .font-mono { font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, monospace; }\n");
        html.append("    .badge { display: inline-flex; align-items: center; gap: 6px; }\n");
        html.append("    .modal-overlay { display: none; position: fixed; inset: 0; z-index: 50; background: rgba(15, 23, 42, 0.45); backdrop-filter: blur(4px); }\n");
        html.append("    .modal { display: none; position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 60; width: 90%; max-width: 480px; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); }\n");
        html.append("    .modal.show { display: block; }\n");
        html.append("    .modal-overlay.show { display: block; }\n");
        html.append("    .btn-primary { background: #059669; color: #ffffff; }\n");
        html.append("    .btn-primary:hover { background: #047857; }\n");
        html.append("    .avatar { width: 32px; height: 32px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #ffffff; }\n");
        html.append("    .input-field { width: 100%; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; color: #0f172a; }\n");
        html.append("    .input-field:focus { outline: none; border-color: #059669; ring: 2px solid #059669; }\n");
        html.append("    .table-row:hover { background: #f8fafc; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body class=\"bg-[#F8FAFC] text-slate-900 antialiased\">\n");

        // Navbar
        html.append("<nav class=\"bg-white border-b border-slate-200 sticky top-0 z-50\">\n");
        html.append("  <div class=\"max-w-7xl mx-auto px-4 sm:px-6 lg:px-8\">\n");
        html.append("    <div class=\"flex justify-between h-16 items-center\">\n");
        html.append("      <div class=\"flex items-center gap-2\">\n");
        html.append("        <div class=\"w-8 h-8 bg-emerald-600 rounded-lg flex items-center justify-center text-white font-bold text-lg\">S</div>\n");
        html.append("        <span class=\"font-bold text-xl tracking-tight text-slate-900\">SplitEase</span>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"flex items-center gap-3\">\n");
        html.append("        <div class=\"flex items-center gap-1.5 px-2.5 py-1 rounded-full border border-emerald-100 bg-emerald-50\">\n");
        html.append("          <span class=\"w-1.5 h-1.5 rounded-full bg-emerald-500\"></span>\n");
        html.append("          <span class=\"text-xs font-medium text-emerald-700 font-mono\">MySQL + JDBC Connected</span>\n");
        html.append("        </div>\n");
        html.append("        <button onclick=\"openModal('addExpenseModal')\" class=\"btn-primary px-4 py-2 rounded-lg text-sm font-medium hover:opacity-90 transition\">Add Expense</button>\n");
        html.append("        <button onclick=\"openModal('addUserModal')\" class=\"border border-slate-200 bg-white px-4 py-2 rounded-lg text-sm font-medium text-slate-700 hover:bg-slate-50 transition\">Add Member</button>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        html.append("</nav>\n");

        // Page header
        html.append("<main class=\"max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8\">\n");
        html.append("  <div class=\"mb-8\">\n");
        html.append("    <h1 class=\"text-3xl font-bold text-slate-900\">Dashboard</h1>\n");
        html.append("    <p class=\"text-slate-600 mt-1\">Track group expenses and settle up with friends.</p>\n");
        html.append("  </div>\n");

        // Error banner
        if (error != null) {
            html.append("  <div class=\"mb-6 p-4 rounded-lg bg-red-50 border border-red-200\">\n");
            html.append("    <p class=\"text-sm text-red-700\">").append(escape(error)).append("</p>\n");
            html.append("  </div>\n");
        }

        // Summary cards
        html.append("  <div class=\"grid grid-cols-1 md:grid-cols-3 gap-6 mb-8\">\n");
        html.append("    <div class=\"bg-white rounded-xl border border-slate-200 p-6 shadow-sm\">\n");
        html.append("      <div class=\"flex items-center justify-between\">\n");
        html.append("        <div>\n");
        html.append("          <p class=\"text-sm font-medium text-slate-500\">Total Group Spend</p>\n");
        html.append("          <p class=\"text-2xl font-bold font-mono text-slate-900 mt-1\">").append(escape(money(totalSpend))).append("</p>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"w-8 h-8 rounded-lg bg-indigo-100 flex items-center justify-center text-indigo-600 text-lg font-bold\">₹</div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"bg-white rounded-xl border border-slate-200 p-6 shadow-sm\">\n");
        html.append("      <div class=\"flex items-center justify-between\">\n");
        html.append("        <div>\n");
        html.append("          <p class=\"text-sm font-medium text-slate-500\">Total Members</p>\n");
        html.append("          <p class=\"text-2xl font-bold font-mono text-slate-900 mt-1\">").append(users.size()).append("</p>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"w-8 h-8 rounded-lg bg-emerald-100 flex items-center justify-center text-emerald-600 text-lg font-bold\">👥</div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"bg-white rounded-xl border border-slate-200 p-6 shadow-sm\">\n");
        html.append("      <div class=\"flex items-center justify-between\">\n");
        html.append("      <p class=\"text-2xl font-bold ").append(pendingCount == 0 ? "text-emerald-600" : "text-amber-600").append(" mt-1\">")
                .append(pendingCount).append(" split(s)</p>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        // Modals (triggered by navbar buttons)
        // ---- Add Expense Modal ----
        html.append("    <div id=\"addExpenseModal\" class=\"modal-overlay\" onclick=\"closeModal('addExpenseModal')\">\n");
        html.append("      <div class=\"modal\" onclick=\"event.stopPropagation()\">\n");
        html.append("        <div class=\"p-6\">\n");
        html.append("          <h3 class=\"text-lg font-bold text-slate-900 mb-4\">Add Expense</h3>\n");
        html.append("          <form action=\"/add-expense\" method=\"post\" class=\"space-y-4\">\n");
        html.append("            <div>\n");
        html.append("              <label class=\"block text-sm font-medium text-slate-700 mb-1\">Description</label>\n");
        html.append("              <input type=\"text\" name=\"description\" required class=\"input-field\">\n");
        html.append("            </div>\n");
        html.append("            <div>\n");
        html.append("              <label class=\"block text-sm font-medium text-slate-700 mb-1\">Total Amount (₹)</label>\n");
        html.append("              <input type=\"number\" name=\"total_amount\" step=\"0.01\" min=\"0\" required class=\"input-field\">\n");
        html.append("            </div>\n");
        html.append("            <div>\n");
        html.append("              <label class=\"block text-sm font-medium text-slate-700 mb-1\">Paid By</label>\n");
        html.append("              <select name=\"paid_by\" class=\"input-field\">\n");
        for (User u : users) {
            html.append("                <option value=\"").append(u.getUserId()).append("\">").append(escape(u.getName())).append("</option>\n");
        }
        html.append("              </select>\n");
        html.append("            </div>\n");
        html.append("            <div>\n");
        html.append("              <label class=\"block text-sm font-medium text-slate-700 mb-1\">Category</label>\n");
        html.append("              <select name=\"category\" class=\"input-field\">\n");
        String[] defaultCategories = {"Food", "Travel", "Shopping", "Utilities", "Entertainment", "Other"};
        for (String c : defaultCategories) {
            html.append("                <option value=\"").append(c).append("\">").append(c).append("</option>\n");
        }
        html.append("              </select>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"flex justify-end gap-3 pt-4\">\n");
        html.append("              <button type=\"button\" onclick=\"closeModal('addExpenseModal')\" class=\"px-4 py-2 rounded-lg border border-slate-200 text-slate-700 font-medium hover:bg-slate-50\">Cancel</button>\n");
        html.append("              <button type=\"submit\" class=\"btn-primary px-4 py-2 rounded-lg text-sm font-medium\">Add Expense</button>\n");
        html.append("            </div>\n");
        html.append("            <p class=\"mt-3 text-xs text-slate-500\">Splits the amount equally among all members automatically.</p>\n");
        html.append("          </form>\n");
        html.append("        </div>\n");
        html.append("        <button onclick=\"closeModal('addExpenseModal')\" class=\"absolute right-3 top-3 text-slate-400 hover:text-slate-600\">&times;</button>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");

        // ---- Add User Modal ----
        html.append("    <div id=\"addUserModal\" class=\"modal-overlay\" onclick=\"closeModal('addUserModal')\">\n");
        html.append("      <div class=\"modal\" onclick=\"event.stopPropagation()\">\n");
        html.append("        <div class=\"p-6\">\n");
        html.append("          <h3 class=\"text-lg font-bold text-slate-900 mb-4\">Add Member</h3>\n");
        html.append("          <form action=\"/add-user\" method=\"post\" class=\"space-y-4\">\n");
        html.append("            <div>\n");
        html.append("              <label class=\"block text-sm font-medium text-slate-700 mb-1\">Name</label>\n");
        html.append("              <input type=\"text\" name=\"name\" required class=\"input-field\">\n");
        html.append("            </div>\n");
        html.append("            <div>\n");
        html.append("              <label class=\"block text-sm font-medium text-slate-700 mb-1\">Email</label>\n");
        html.append("              <input type=\"email\" name=\"email\" required class=\"input-field\">\n");
        html.append("            </div>\n");
        html.append("            <div class=\"flex justify-end pt-4\">\n");
        html.append("              <button type=\"button\" onclick=\"closeModal('addUserModal')\" class=\"px-4 py-2 rounded-lg border border-slate-200 text-slate-700 font-medium hover:bg-slate-50\">Cancel</button>\n");
        html.append("              <button type=\"submit\" class=\"btn-primary px-4 py-2 rounded-lg text-sm font-medium\">Add Member</button>\n");
        html.append("            </div>\n");
        html.append("          </form>\n");
        html.append("        </div>\n");
        html.append("        <button onclick=\"closeModal('addUserModal')\" class=\"absolute right-3 top-3 text-slate-400 hover:text-slate-600\">&times;</button>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        // Bottom row: Recent Expenses + Member Balances + Category Chart
        html.append("  <div class=\"grid grid-cols-1 lg:grid-cols-12 gap-6\">\n");

        html.append("    <div class=\"lg:col-span-8 bg-white rounded-xl border border-slate-200 p-6 shadow-sm\">\n");
        html.append("      <div class=\"flex items-center justify-between mb-4\">\n");
        html.append("        <h2 class=\"text-lg font-bold text-slate-900\">Recent Expenses</h2>\n");
        html.append("        <div class=\"relative\">\n");
        html.append("          <input type=\"text\" id=\"expenseSearch\" placeholder=\"Search expenses…\" class=\"input-field pl-9 pr-4 py-2 max-w-xs\">\n");
        html.append("          <svg class=\"w-4 h-4 text-slate-400 absolute left-3 top-2.5\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z\"></path></svg>\n");
        html.append("        </div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"overflow-x-auto\">\n");
        html.append("        <table class=\"w-full text-left border-collapse\" id=\"expenseTable\">\n");
        html.append("          <thead>\n");
        html.append("            <tr class=\"border-b border-slate-200\">\n");
        html.append("              <th class=\"pb-3 text-sm font-medium text-slate-500\">Description</th>\n");
        html.append("              <th class=\"pb-3 text-sm font-medium text-slate-500\">Payer</th>\n");
        html.append("              <th class=\"pb-3 text-sm font-medium text-slate-500\">Category</th>\n");
        html.append("              <th class=\"pb-3 text-sm font-medium text-slate-500 text-right\">Amount</th>\n");
        html.append("              <th class=\"pb-3 text-sm font-medium text-slate-500\">Date</th>\n");
        html.append("            </tr>\n");
        html.append("          </thead>\n");
        html.append("          <tbody>\n");
        if (expenses.isEmpty()) {
            html.append("            <tr><td colspan=\"5\" class=\"py-4 text-center text-slate-500\">No expenses yet.</td></tr>\n");
        }
        for (Expense e : expenses) {
            String payerInitial = e.getPayerName().substring(0, 1).toUpperCase();
            int payerHash = payerInitial.hashCode() & 0x7FFFFFFF;
            String payerColor = String.format("#%06X", payerHash % 0x1000000);
            if (payerColor.equals("#000000")) payerColor = "#059669";
            html.append("            <tr class=\"table-row border-b border-slate-100 hover:bg-slate-50 transition\">\n");
            html.append("              <td class=\"py-4 text-slate-900 font-medium\">").append(escape(e.getDescription())).append("</td>\n");
            html.append("              <td class=\"py-4 text-sm\"><span class=\"avatar\" style=\"background:").append(payerColor).append(";\">").append(escape(payerInitial)).append("</span> <span class=\"text-slate-600 ml-2\">").append(escape(e.getPayerName())).append("</span></td>\n");
            html.append("              <td class=\"py-4\"><span class=\"inline-block px-2 py-1 text-xs font-medium rounded-full bg-indigo-100 text-indigo-700\">").append(escape(e.getCategory())).append("</span></td>\n");
            html.append("              <td class=\"py-4 text-right text-slate-900 font-medium font-mono\">").append(escape(money(e.getTotalAmount()))).append("</td>\n");
            html.append("              <td class=\"py-4 text-slate-500 text-sm\">").append(escape(e.getCreatedAt() != null ? e.getCreatedAt().toString() : "-")).append("</td>\n");
            html.append("            </tr>\n");
        }
        html.append("          </tbody>\n");
        html.append("        </table>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        // ---- Right column: Member Balances + Category Chart ----
        html.append("    <div class=\"lg:col-span-4 flex flex-col gap-6\">\n");

        // Member balances card
        html.append("      <div class=\"bg-white rounded-xl border border-slate-200 p-6 shadow-sm flex-1\">\n");
        html.append("        <h2 class=\"text-lg font-bold text-slate-900 mb-4\">Member Balances</h2>\n");
        if (balances.isEmpty()) {
            html.append("        <p class=\"text-slate-500 text-sm\">No members yet.</p>\n");
        } else {
            html.append("        <div class=\"space-y-3\">\n");
            for (UserBalance b : balances) {
                String initial = b.getName().substring(0, 1).toUpperCase();
                int h = initial.hashCode() & 0x7FFFFFFF;
                String color = String.format("#%06X", h % 0x1000000);
                if (color.equals("#000000")) color = "#059669";
                html.append("          <div class=\"flex items-center justify-between p-3 rounded-lg border border-slate-200 bg-white\">\n");
                html.append("            <div class=\"flex items-center gap-3\">\n");
                html.append("              <span class=\"avatar\" style=\"background:").append(color).append(";\">").append(escape(initial)).append("</span>\n");
                html.append("              <div>\n");
                html.append("                <span class=\"font-medium text-slate-900\">").append(escape(b.getName())).append("</span>\n");
                html.append("                <p class=\"text-xs text-slate-500\">paid ").append(escape(money(b.getTotalPaid()))).append(" \u00b7 owes ").append(escape(money(b.getTotalOwed()))).append("</p>\n");
                html.append("              </div>\n");
                html.append("            </div>\n");
                html.append("            <div class=\"text-right flex items-center gap-2\">\n");
                if (b.getNetBalance() > 0) {
                    html.append("              <span class=\"inline-block px-2.5 py-0.5 rounded text-xs font-bold bg-emerald-100 text-emerald-700\">+ Gets back ").append(escape(money(b.getNetBalance()))).append("</span>\n");
                } else if (b.getNetBalance() < 0) {
                    html.append("              <span class=\"inline-block px-2.5 py-0.5 rounded text-xs font-bold bg-red-100 text-red-700\">- Owes ").append(escape(money(-b.getNetBalance()))).append("</span>\n");
                    html.append("              <a href=\"/settle?user_id=").append(b.getUserId()).append("\"\n");
                    html.append("                 class=\"text-xs font-medium text-red-600 hover:text-red-800 underline hover:underline\">Settle Up</a>\n");
                } else {
                    html.append("              <span class=\"inline-block px-2.5 py-0.5 rounded text-xs font-bold bg-slate-100 text-slate-600\">Settled</span>\n");
                }
                html.append("            </div>\n");
                html.append("          </div>\n");
            }
            html.append("        </div>\n");
        }
        html.append("      </div>\n");

        // Category doughnut chart card
        html.append("      <div class=\"bg-white rounded-xl border border-slate-200 p-6 shadow-sm flex-1\">\n");
        html.append("        <h2 class=\"text-lg font-bold text-slate-900 mb-4\">Category Breakdown</h2>\n");
        html.append("        <div class=\"h-64\">\n");
        html.append("          <canvas id=\"categoryChart\"></canvas>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"mt-4 space-y-2\">\n");
        if (categories.isEmpty()) {
            html.append("          <p class=\"text-sm text-slate-500\">No spending yet.</p>\n");
        }
        String[] palette = {"#4f46e5", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#06b6d4", "#ec4899", "#6366f1"};
        for (int i = 0; i < categories.size(); i++) {
            CategorySummary cs = categories.get(i);
            html.append("          <div class=\"flex items-center justify-between text-sm\">\n");
            html.append("            <span class=\"text-slate-600\">").append(escape(cs.getCategory())).append("</span>\n");
            html.append("            <span class=\"font-medium text-slate-900\">").append(escape(money(cs.getTotal()))).append("</span>\n");
            html.append("          </div>\n");
        }
        html.append("        </div>\n");
        html.append("      </div>\n");

        html.append("    </div>\n"); // right column
        html.append("  </div>\n"); // bottom row
        html.append("</main>\n");

        // Chart.js doughnut chart
        html.append("  <script>\n");
        html.append("    const labels = [");
        for (int i = 0; i < categories.size(); i++) {
            html.append("\"").append(escape(categories.get(i).getCategory())).append("\"");
            if (i < categories.size() - 1) html.append(", ");
        }
        html.append("];\n");
        html.append("    const data = [");
        for (int i = 0; i < categories.size(); i++) {
            html.append(Double.toString(categories.get(i).getTotal()));
            if (i < categories.size() - 1) html.append(", ");
        }
        html.append("];\n");
        html.append("    const colors = [");
        for (int i = 0; i < categories.size(); i++) {
            html.append("\"").append(palette[i % palette.length]).append("\"");
            if (i < categories.size() - 1) html.append(", ");
        }
        html.append("];\n");
        html.append("    new Chart(document.getElementById('categoryChart'), {\n");
        html.append("      type: 'doughnut',\n");
        html.append("      data: {\n");
        html.append("        labels: labels,\n");
        html.append("        datasets: [{ data: data, backgroundColor: colors, borderWidth: 0, hoverOffset: 20 }]\n");
        html.append("      },\n");
        html.append("      options: {\n");
        html.append("        responsive: true,\n");
        html.append("        maintainAspectRatio: false,\n");
        html.append("        plugins: {\n");
        html.append("          legend: { position: 'bottom', labels: { font: { family: 'Inter' } } },\n");
        html.append("          tooltip: { callbacks: { label: ctx => ctx.label + ': \\u20B9' + ctx.parsed.toFixed(2) } }\n");
        html.append("        }\n");
        html.append("      }\n");
        html.append("    });\n");
        html.append("  </script>\n");
        html.append("  <script>\n");
        html.append("    function openModal(modalId) {\n");
        html.append("      var overlay = document.getElementById(modalId);\n");
        html.append("      overlay.classList.add('show');\n");
        html.append("    }\n");
        html.append("    function closeModal(modalId) {\n");
        html.append("      document.getElementById(modalId).classList.remove('show');\n");
        html.append("    }\n");
        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    /* ------------------------- Form handlers ------------------------- */

    static final class AddUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendRedirect(exchange, "/");
                return;
            }
            try {
                Map<String, String> form = parseForm(exchange.getRequestBody());
                dao.addUser(form.get("name"), form.get("email"));
                sendRedirect(exchange, "/");
            } catch (SQLException e) {
                e.printStackTrace();
                sendRedirect(exchange, "/?error=add_user_failed");
            }
        }
    }

    static final class AddExpenseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendRedirect(exchange, "/");
                return;
            }
            try {
                Map<String, String> form = parseForm(exchange.getRequestBody());
                String description = form.get("description");
                double total = Double.parseDouble(form.get("total_amount"));
                int paidBy = Integer.parseInt(form.get("paid_by"));
                String category = form.get("category");
                dao.addExpenseWithEqualSplit(description, total, paidBy, category);
                sendRedirect(exchange, "/");
            } catch (SQLException | NumberFormatException e) {
                e.printStackTrace();
                sendRedirect(exchange, "/?error=add_expense_failed");
            }
        }
    }

    static final class SettleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = null;
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("user_id=")) {
                            userIdParam = urlDecode(param.substring("user_id=".length()));
                            break;
                        }
                    }
                }
                if (userIdParam != null) {
                    dao.settleUserBalance(Integer.parseInt(userIdParam));
                }
                sendRedirect(exchange, "/");
            } catch (SQLException | NumberFormatException e) {
                e.printStackTrace();
                sendRedirect(exchange, "/?error=settle_failed");
            }
        }
    }
}
