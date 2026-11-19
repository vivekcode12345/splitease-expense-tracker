# SplitEase — Expense & Split-Bill Tracker

A clean, dependency-free Java web application for tracking shared expenses and settling up with friends. Built with **Core Java (JDK `com.sun.net.httpserver.HttpServer`) + Pure JDBC + MySQL**, with a modern **Tailwind CSS + Chart.js** frontend served as HTML straight from Java.

No Spring Boot, no Maven, no Tomcat — compiles and runs with a single `javac` / `java` command.

## Tech Stack

| Layer | Technology |
|-------|------------|
| HTTP server | JDK built-in `com.sun.net.httpserver.HttpServer` (port 8080) |
| Database access | Pure JDBC (`DriverManager`, `PreparedStatement`, `ResultSet`) |
| Database | MySQL (`split_tracker`) |
| Frontend | Tailwind CSS (CDN), Chart.js (CDN), server-side rendered HTML |
| Build | Plain `javac` / `java`, classpath = `lib/*` |

## Project Structure

```
splitease/
├── README.md
├── .gitignore
├── database/
│   └── schema.sql          # Full DDL + seed data for the split_tracker DB
├── lib/
│   └── mysql-connector-j-8.4.0.jar   # MySQL JDBC driver (downloaded from Maven Central)
├── bin/                    # Compiled output (gitignored)
└── src/com/splitease/
    ├── db/
    │   └── DBConnection.java      # Single JDBC connection point (Class.forName + DriverManager)
    ├── dao/
    │   ├── ExpenseDAO.java        # All data access: users, expenses, splits, balances, settle
    │   ├── User.java
    │   ├── Expense.java
    │   ├── UserBalance.java
    │   ├── CategorySummary.java
    │   └── ...
    └── web/
        └── MainServer.java        # HttpServer, endpoints, and the Tailwind + Chart.js dashboard
```

## Database

The MySQL database `split_tracker` with tables `users`, `expenses`, `expense_splits` is expected to exist locally on `localhost:3306`. See `database/schema.sql` for the exact DDL and seed data.

## JDBC Highlights

- **`DBConnection`** — loads `com.mysql.cj.jdbc.Driver` via `Class.forName` and exposes `DriverManager.getConnection("jdbc:mysql://localhost:3306/split_tracker?useSSL=false&allowPublicKeyRetrieval=true", "root", <password>)`. The password comes from the `DB_PASSWORD` environment variable so no secrets are committed. (`allowPublicKeyRetrieval=true` is needed for MySQL 8's `caching_sha2_password` over plain TCP.)
- **`ExpenseDAO`** — every query uses `PreparedStatement` (no string concatenation in SQL) with try-with-resources:
  - `getAllUsers` / `addUser` — member management
  - `addExpenseWithEqualSplit` — inserts an expense and creates equal splits for **all current users** inside one JDBC transaction (`setAutoCommit(false)` / `commit` / `rollback`)
  - `getAllExpenses` — `JOIN` with `users` to show payer names
  - `getUserBalances` — `JOIN` + `SUM` aggregates: net balance = total paid − total owed per user
  - `settleUserBalance` — marks a user's pending splits as settled
  - `getCategorySummary` — `GROUP BY category` + `SUM` for the Chart.js doughnut chart

## Build & Run

### 1. Download the JDBC driver (already in `lib/`)

```bash
curl -fsSL -o lib/mysql-connector-j-8.4.0.jar \
  https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
```

### 2. Compile

```bash
javac -cp "lib/*" -d bin src/com/splitease/db/DBConnection.java src/com/splitease/dao/*.java src/com/splitease/web/MainServer.java
```

### 3. Run

Set your MySQL root password via the `DB_PASSWORD` env var, then start the server:

```bash
export DB_PASSWORD=YOUR_MYSQL_ROOT_PASSWORD
java -cp "bin:lib/*" com.splitease.web.MainServer
```

Open **http://localhost:8080** in your browser.

If your MySQL root has no password, run with `DB_PASSWORD=` (empty string).

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Dashboard: summary cards, forms, balances, recent expenses, category chart |
| POST | `/add-user` | Form: name, email → creates a member, redirects to `/` |
| POST | `/add-expense` | Form: description, amount, payer, category → expense + equal splits for all users, redirects to `/` |
| GET | `/settle?user_id=1` | Marks the given user's pending splits as settled, redirects to `/` |

## Dashboard Features

- Top summary cards: Total Group Spend, Total Members, Pending Settlements
- Add Member and Add Expense forms
- Member Balances: green (`+ Gets back`) / red (`- Owes`) badges with a working **Settle Up** button per member
- Category Breakdown doughnut chart (Chart.js)
- Recent Expenses table with category badges and timestamps

## License

MIT — feel free to use and modify.
