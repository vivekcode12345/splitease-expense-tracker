package com.splitease.expense;

import com.splitease.common.exception.ApiException;
import com.splitease.expense.dto.CreateExpenseRequest;
import com.splitease.expense.dto.CreateExpenseSplitRequest;
import com.splitease.expense.dto.ExpenseResponse;
import com.splitease.expense.dto.ExpenseSplitResponse;
import com.splitease.group.ExpenseGroup;
import com.splitease.group.ExpenseGroupRepository;
import com.splitease.group.GroupMember;
import com.splitease.group.GroupRole;
import com.splitease.group.GroupMemberRepository;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseResponse createExpense(Long groupId, Long currentUserId, CreateExpenseRequest request) {
        ExpenseGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("GROUP_NOT_FOUND", "Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw ApiException.forbidden("NOT_MEMBER", "You are not a member of this group");
        }

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getPaidBy())) {
            throw ApiException.badRequest("INVALID_PAID_BY", "Paid-by user is not a member of this group");
        }

        List<Long> participantIds;
        List<CreateExpenseSplitRequest> splitRequests = request.getSplits();
        if (splitRequests == null || splitRequests.isEmpty()) {
            participantIds = groupMemberRepository.findByGroupId(groupId).stream()
                    .map(GroupMember::getUserId)
                    .toList();
        } else {
            participantIds = splitRequests.stream()
                    .map(CreateExpenseSplitRequest::getUserId)
                    .toList();
            for (Long uid : participantIds) {
                if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, uid)) {
                    throw ApiException.badRequest("INVALID_PARTICIPANT",
                            "User " + uid + " is not a member of this group");
                }
            }
        }

        if (participantIds.isEmpty()) {
            throw ApiException.badRequest("NO_PARTICIPANTS", "Group has no members to split with");
        }

        List<ExpenseSplit> splits = computeSplits(request, participantIds);

        User paidByUser = userRepository.getReferenceById(request.getPaidBy());
        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidByUser)
                .description(request.getDescription())
                .totalAmount(request.getTotalAmount())
                .splitType(request.getSplitType())
                .category(request.getCategory())
                .splits(splits)
                .build();

        splits.forEach(s -> s.setExpense(expense));
        Expense saved = expenseRepository.save(expense);

        return toResponse(saved);
    }

    public List<ExpenseResponse> getExpenses(Long groupId, Long currentUserId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw ApiException.forbidden("NOT_MEMBER", "You are not a member of this group");
        }
        List<Expense> expenses = expenseRepository.findWithSplitsByGroupId(groupId);
        return expenses.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteExpense(Long groupId, Long expenseId, Long currentUserId) {
        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> ApiException.notFound("EXPENSE_NOT_FOUND", "Expense not found"));

        boolean isCreator = expense.getPaidBy().getId().equals(currentUserId);
        boolean isAdmin = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .map(m -> m.getRole() == GroupRole.ADMIN)
                .orElse(false);

        if (!isCreator && !isAdmin) {
            throw ApiException.forbidden("NOT_ALLOWED",
                    "Only the expense creator or a group admin can delete this expense");
        }

        expenseRepository.delete(expense);
    }

    /* ------------------------- Split computation ------------------------- */

    private List<ExpenseSplit> computeSplits(CreateExpenseRequest request, List<Long> participantIds) {
        BigDecimal total = request.getTotalAmount();
        List<ExpenseSplit> splits = new ArrayList<>();

        switch (request.getSplitType()) {
            case EQUAL -> computeEqualSplits(total, participantIds, splits);
            case EXACT -> computeExactSplits(request, total, splits);
            case PERCENTAGE -> computePercentageSplits(request, total, splits);
        }

        return splits;
    }

    private void computeEqualSplits(BigDecimal total, List<Long> participantIds,
                                    List<ExpenseSplit> splits) {
        int n = participantIds.size();
        BigDecimal base = total.divide(new BigDecimal(n), 2, RoundingMode.HALF_UP);
        BigDecimal remainder = total.subtract(base.multiply(new BigDecimal(n)));

        for (int i = 0; i < participantIds.size(); i++) {
            BigDecimal amount = base;
            if (i == 0) {
                amount = amount.add(remainder);
            }
            splits.add(ExpenseSplit.builder()
                    .user(userRepository.getReferenceById(participantIds.get(i)))
                    .owedAmount(amount)
                    .build());
        }
    }

    private void computeExactSplits(CreateExpenseRequest request, BigDecimal total,
                                    List<ExpenseSplit> splits) {
        BigDecimal sum = request.getSplits().stream()
                .map(CreateExpenseSplitRequest::getOwedAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(total) != 0) {
            throw ApiException.badRequest("SPLIT_SUM_MISMATCH",
                    "Sum of exact split amounts must equal the total amount");
        }

        for (CreateExpenseSplitRequest sr : request.getSplits()) {
            splits.add(ExpenseSplit.builder()
                    .user(userRepository.getReferenceById(sr.getUserId()))
                    .owedAmount(sr.getOwedAmount())
                    .build());
        }
    }

    private void computePercentageSplits(CreateExpenseRequest request, BigDecimal total,
                                         List<ExpenseSplit> splits) {
        List<CreateExpenseSplitRequest> splitRequests = request.getSplits();

        BigDecimal pctSum = splitRequests.stream()
                .map(CreateExpenseSplitRequest::getPercentage)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (pctSum.compareTo(new BigDecimal("100.00")) != 0) {
            throw ApiException.badRequest("PERCENTAGE_SUM_MISMATCH",
                    "Percentages must sum to 100.00");
        }

        BigDecimal runningSubtract = BigDecimal.ZERO;
        int lastIdx = splitRequests.size() - 1;

        for (int i = 0; i < splitRequests.size(); i++) {
            CreateExpenseSplitRequest sr = splitRequests.get(i);
            BigDecimal amount;
            if (i == lastIdx) {
                amount = total.subtract(runningSubtract);
            } else {
                amount = total.multiply(sr.getPercentage())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
                runningSubtract = runningSubtract.add(amount);
            }
            splits.add(ExpenseSplit.builder()
                    .user(userRepository.getReferenceById(sr.getUserId()))
                    .owedAmount(amount)
                    .percentage(sr.getPercentage())
                    .build());
        }
    }

    /* ------------------------- Response mapping ------------------------- */

    private ExpenseResponse toResponse(Expense expense) {
        List<ExpenseSplitResponse> splitResponses = expense.getSplits().stream()
                .map(s -> ExpenseSplitResponse.builder()
                        .id(s.getId())
                        .userId(s.getUser().getId())
                        .userName(s.getUser().getName())
                        .owedAmount(s.getOwedAmount())
                        .percentage(s.getPercentage())
                        .build())
                .toList();

        return ExpenseResponse.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .totalAmount(expense.getTotalAmount())
                .splitType(expense.getSplitType())
                .category(expense.getCategory())
                .paidBy(expense.getPaidBy().getId())
                .paidByName(expense.getPaidBy().getName())
                .splits(splitResponses)
                .createdAt(expense.getCreatedAt())
                .build();
    }
}