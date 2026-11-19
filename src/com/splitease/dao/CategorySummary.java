package com.splitease.dao;

public class CategorySummary {
    private final String category;
    private final double total;

    public CategorySummary(String category, double total) {
        this.category = category;
        this.total = total;
    }

    public String getCategory() { return category; }
    public double getTotal() { return total; }
}
