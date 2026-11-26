package com.splitease.settlement;

import com.splitease.settlement.dto.BalancesResponse;
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
}