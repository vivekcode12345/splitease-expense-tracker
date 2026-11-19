package com.splitease.dao;

public class UserBalance {
    private final int userId;
    private final String name;
    private final double totalPaid;
    private final double totalOwed;
    private final double netBalance;

    public UserBalance(int userId, String name, double totalPaid, double totalOwed, double netBalance) {
        this.userId = userId;
        this.name = name;
        this.totalPaid = totalPaid;
        this.totalOwed = totalOwed;
        this.netBalance = netBalance;
    }

    public int getUserId() { return userId; }
    public String getName() { return name; }
    public double getTotalPaid() { return totalPaid; }
    public double getTotalOwed() { return totalOwed; }
    public double getNetBalance() { return netBalance; }

    /** Positive: others owe this user (gets back). Negative: this user owes others. */
    public boolean isOwed() { return netBalance > 0; }
}
