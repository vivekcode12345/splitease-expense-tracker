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
public class SimplifiedDebtsResponse {

    private Long groupId;
    private int rawTransactionCount;
    private int simplifiedTransactionCount;
    private int transactionsSaved;
    private List<SimplifiedTransactionDto> transactions;
}