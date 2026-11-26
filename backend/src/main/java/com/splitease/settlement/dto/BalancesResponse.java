package com.splitease.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalancesResponse {

    private List<BalanceResponse> members;
    private List<CategorySummaryResponse> categorySummary;
}