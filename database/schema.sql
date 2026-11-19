-- SplitEase (Expense & Split-Bill Tracker) Database Schema
-- Database: split_tracker (MySQL)

CREATE DATABASE IF NOT EXISTS split_tracker
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE split_tracker;

DROP TABLE IF EXISTS expense_splits;
DROP TABLE IF EXISTS expenses;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  user_id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expenses (
  expense_id INT NOT NULL AUTO_INCREMENT,
  description VARCHAR(255) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  paid_by INT NOT NULL,
  category VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (expense_id),
  CONSTRAINT fk_expenses_paid_by FOREIGN KEY (paid_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_splits (
  split_id INT NOT NULL AUTO_INCREMENT,
  expense_id INT NOT NULL,
  user_id INT NOT NULL,
  owed_amount DECIMAL(10,2) NOT NULL,
  is_settled TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (split_id),
  CONSTRAINT fk_splits_expense FOREIGN KEY (expense_id) REFERENCES expenses(expense_id) ON DELETE CASCADE,
  CONSTRAINT fk_splits_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed data
INSERT INTO users (name, email) VALUES
  ('Vivek', 'vivek@example.com'),
  ('Rajan', 'rajan@example.com'),
  ('Ansh', 'ansh@example.com');

INSERT INTO expenses (description, total_amount, paid_by, category) VALUES
  ('Hostel Night Biryani', 900.00, 1, 'Food'),
  ('Auto to Station', 300.00, 2, 'Travel');

INSERT INTO expense_splits (expense_id, user_id, owed_amount, is_settled) VALUES
  (1, 1, 300.00, 1),
  (1, 2, 300.00, 0),
  (1, 3, 300.00, 0),
  (2, 1, 100.00, 0),
  (2, 2, 100.00, 1),
  (2, 3, 100.00, 0);