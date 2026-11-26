package com.splitease.settlement;

import com.splitease.common.exception.ApiException;
import com.splitease.settlement.dto.BalancesResponse;
import com.splitease.settlement.dto.SimplifiedDebtsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.splitease.user.User;

@RestController
@RequestMapping("/v1/groups/{groupId}/balances")
@RequiredArgsConstructor
public class BalanceController {

    private final SettlementService settlementService;

    @GetMapping
    public ResponseEntity<BalancesResponse> getBalances(
            @PathVariable Long groupId,
            Authentication authentication) {
        Long currentUserId = ((User) authentication.getPrincipal()).getId();
        BalancesResponse balances = settlementService.getBalances(groupId, currentUserId);
        return ResponseEntity.ok(balances);
    }

    @GetMapping("/simplify")
    public ResponseEntity<SimplifiedDebtsResponse> simplify(
            @PathVariable Long groupId,
            Authentication authentication) {
        Long currentUserId = ((User) authentication.getPrincipal()).getId();
        if (!settlementService.isMember(groupId, currentUserId)) {
            throw ApiException.forbidden("NOT_MEMBER", "You are not a member of this group");
        }
        var result = settlementService.simplifyGroupDebts(groupId);
        SimplifiedDebtsResponse response = SimplifiedDebtsResponse.builder()
                .groupId(groupId)
                .rawTransactionCount(result.getRawTransactionCount())
                .simplifiedTransactionCount(result.getSimplifiedTransactionCount())
                .transactionsSaved(result.getTransactionsSaved())
                .transactions(result.getTransactions())
                .build();
        return ResponseEntity.ok(response);
    }
}