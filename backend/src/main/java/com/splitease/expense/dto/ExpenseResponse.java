package com.splitease.expense.dto;

import com.splitease.expense.SplitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private String description;
    private BigDecimal totalAmount;
    private SplitType splitType;
    private String category;
    private Long paidBy;
    private String paidByName;
    private List<ExpenseSplitResponse> splits;
    private Instant createdAt;
}