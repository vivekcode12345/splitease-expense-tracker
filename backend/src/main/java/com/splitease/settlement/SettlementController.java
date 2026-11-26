package com.splitease.settlement;

import com.splitease.settlement.dto.CreateSettlementRequest;
import com.splitease.settlement.dto.SettlementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.splitease.user.User;

import java.util.List;

@RestController
@RequestMapping("/v1/groups/{groupId}/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementResponse> createSettlement(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateSettlementRequest request,
            Authentication authentication) {
        Long currentUserId = ((User) authentication.getPrincipal()).getId();
        SettlementResponse response = settlementService.createSettlement(groupId, currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SettlementResponse>> getSettlements(
            @PathVariable Long groupId,
            Authentication authentication) {
        Long currentUserId = ((User) authentication.getPrincipal()).getId();
        List<SettlementResponse> settlements = settlementService.getSettlements(groupId, currentUserId);
        return ResponseEntity.ok(settlements);
    }
}