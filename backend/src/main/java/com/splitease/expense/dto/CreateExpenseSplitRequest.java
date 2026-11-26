package com.splitease.expense.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseSplitRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private BigDecimal owedAmount;

    private BigDecimal percentage;
}