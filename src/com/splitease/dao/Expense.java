package com.splitease.dao;

import java.sql.Timestamp;

public class Expense {
    private final int expenseId;
    private final String description;
    private final double totalAmount;
    private final int paidBy;
    private final String payerName;
    private final String category;
    private final Timestamp createdAt;

    public Expense(int expenseId, String description, double totalAmount,
                   int paidBy, String payerName, String category, Timestamp createdAt) {
        this.expenseId = expenseId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.paidBy = paidBy;
        this.payerName = payerName;
        this.category = category;
        this.createdAt = createdAt;
    }

    public int getExpenseId() { return expenseId; }
    public String getDescription() { return description; }
    public double getTotalAmount() { return totalAmount; }
    public int getPaidBy() { return paidBy; }
    public String getPayerName() { return payerName; }
    public String getCategory() { return category; }
    public Timestamp getCreatedAt() { return createdAt; }
}
