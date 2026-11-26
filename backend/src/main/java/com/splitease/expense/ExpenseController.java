package com.splitease.expense;

import com.splitease.expense.dto.CreateExpenseRequest;
import com.splitease.expense.dto.ExpenseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.splitease.user.User;

import java.util.List;

@RestController
@RequestMapping("/v1/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateExpenseRequest request,
            Authentication authentication) {
        Long currentUserId = ((User) authentication.getPrincipal()).getId();
        ExpenseResponse response = expenseService.createExpense(groupId, currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> getExpenses(
            @PathVariable Long groupId,
            Authentication authentication) {
        Long currentUserId = ((User) authentication.getPrincipal()).getId();
        List<ExpenseResponse> expenses = expenseService.getExpenses(groupId, currentUserId);
        return ResponseEntity.ok(expenses);
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            Authentication authentication) {
        Long currentUserId = ((User) authentication.getPrincipal()).getId();
        expenseService.deleteExpense(groupId, expenseId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}