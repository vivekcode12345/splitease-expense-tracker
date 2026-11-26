package com.splitease.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSplitResponse {

    private Long id;
    private Long userId;
    private String userName;
    private BigDecimal owedAmount;
    private BigDecimal percentage;
}