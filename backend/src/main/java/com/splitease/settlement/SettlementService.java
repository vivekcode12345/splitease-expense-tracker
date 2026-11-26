package com.splitease.settlement;

import com.splitease.common.exception.ApiException;
import com.splitease.expense.ExpenseSplitRepository;
import com.splitease.expense.ExpenseRepository;
import com.splitease.group.ExpenseGroup;
import com.splitease.group.ExpenseGroupRepository;
import com.splitease.group.GroupMember;
import com.splitease.group.GroupMemberRepository;
import com.splitease.settlement.dto.BalanceResponse;
import com.splitease.settlement.dto.BalancesResponse;
import com.splitease.settlement.dto.CategorySummaryResponse;
import com.splitease.settlement.dto.CreateSettlementRequest;
import com.splitease.settlement.dto.DebtSimplificationResult;
import com.splitease.settlement.dto.MemberBalanceDto;
import com.splitease.settlement.dto.SettlementResponse;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final DebtSimplificationService debtSimplificationService;

    @Transactional
    public SettlementResponse createSettlement(Long groupId, Long currentUserId,
                                               CreateSettlementRequest request) {
        ExpenseGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("GROUP_NOT_FOUND", "Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw ApiException.forbidden("NOT_MEMBER", "You are not a member of this group");
        }

        if (request.getPayerId().equals(request.getPayeeId())) {
            throw ApiException.badRequest("SAME_USER", "Payer and payee must be different users");
        }

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getPayerId())) {
            throw ApiException.badRequest("INVALID_PAYER", "Payer is not a member of this group");
        }

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getPayeeId())) {
            throw ApiException.badRequest("INVALID_PAYEE", "Payee is not a member of this group");
        }

        User payer = userRepository.getReferenceById(request.getPayerId());
        User payee = userRepository.getReferenceById(request.getPayeeId());

        Settlement settlement = Settlement.builder()
                .group(group)
                .payer(payer)
                .payee(payee)
                .amount(request.getAmount())
                .notes(request.getNotes())
                .settledAt(Instant.now())
                .build();

        settlement = settlementRepository.save(settlement);
        return toResponse(settlement);
    }

    public List<SettlementResponse> getSettlements(Long groupId, Long currentUserId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw ApiException.forbidden("NOT_MEMBER", "You are not a member of this group");
        }
        return settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    public BalancesResponse getBalances(Long groupId, Long currentUserId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw ApiException.forbidden("NOT_MEMBER", "You are not a member of this group");
        }

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            return BalancesResponse.builder()
                    .members(List.of())
                    .categorySummary(List.of())
                    .build();
        }

        Map<Long, BigDecimal> totalPaid = new HashMap<>();
        for (Object[] row : expenseRepository.totalPaidByMember(groupId)) {
            totalPaid.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        Map<Long, BigDecimal> totalOwed = new HashMap<>();
        for (Object[] row : expenseSplitRepository.totalOwedByMember(groupId)) {
            totalOwed.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        Map<Long, BigDecimal> settlementsPaid = new HashMap<>();
        for (Object[] row : settlementRepository.totalPaidByMember(groupId)) {
            settlementsPaid.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        Map<Long, BigDecimal> settlementsReceived = new HashMap<>();
        for (Object[] row : settlementRepository.totalReceivedByMember(groupId)) {
            settlementsReceived.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        BigDecimal ZERO = BigDecimal.ZERO;

        List<BalanceResponse> balanceResponses = members.stream()
                .map(member -> {
                    Long uid = member.getUserId();
                    User user = userRepository.getReferenceById(uid);

                    BigDecimal paid = totalPaid.getOrDefault(uid, ZERO);
                    BigDecimal owed = totalOwed.getOrDefault(uid, ZERO);
                    BigDecimal settledPaid = settlementsPaid.getOrDefault(uid, ZERO);
                    BigDecimal settledReceived = settlementsReceived.getOrDefault(uid, ZERO);

                    BigDecimal netBalance = paid.add(settledPaid)
                            .subtract(owed.add(settledReceived));

                    return BalanceResponse.builder()
                            .userId(uid)
                            .name(user.getName())
                            .totalPaid(paid)
                            .totalOwed(owed)
                            .netBalance(netBalance)
                            .build();
                })
                .sorted((a, b) -> b.getNetBalance().compareTo(a.getNetBalance()))
                .toList();

        List<CategorySummaryResponse> categorySummary = expenseRepository.categorySummary(groupId).stream()
                .map(row -> CategorySummaryResponse.builder()
                        .category((String) row[0])
                        .total((BigDecimal) row[1])
                        .build())
                .toList();

        return BalancesResponse.builder()
                .members(balanceResponses)
                .categorySummary(categorySummary)
                .build();
    }

    /**
     * Fetches each group member's net balance as a {@link MemberBalanceDto} list,
     * suitable for debt simplification. Does NOT perform auth checks (caller is
     * responsible for authorization).
     */
    public List<MemberBalanceDto> getMemberBalances(Long groupId) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> totalPaid = new HashMap<>();
        for (Object[] row : expenseRepository.totalPaidByMember(groupId)) {
            totalPaid.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        Map<Long, BigDecimal> totalOwed = new HashMap<>();
        for (Object[] row : expenseSplitRepository.totalOwedByMember(groupId)) {
            totalOwed.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        Map<Long, BigDecimal> settlementsPaid = new HashMap<>();
        for (Object[] row : settlementRepository.totalPaidByMember(groupId)) {
            settlementsPaid.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        Map<Long, BigDecimal> settlementsReceived = new HashMap<>();
        for (Object[] row : settlementRepository.totalReceivedByMember(groupId)) {
            settlementsReceived.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }

        BigDecimal ZERO = BigDecimal.ZERO;

        List<MemberBalanceDto> dtos = new ArrayList<>();
        for (GroupMember member : members) {
            Long uid = member.getUserId();
            User user = userRepository.getReferenceById(uid);

            BigDecimal paid = totalPaid.getOrDefault(uid, ZERO);
            BigDecimal owed = totalOwed.getOrDefault(uid, ZERO);
            BigDecimal settledPaid = settlementsPaid.getOrDefault(uid, ZERO);
            BigDecimal settledReceived = settlementsReceived.getOrDefault(uid, ZERO);

            BigDecimal netBalance = paid.add(settledPaid)
                    .subtract(owed.add(settledReceived));

            dtos.add(MemberBalanceDto.builder()
                    .userId(uid)
                    .name(user.getName())
                    .netBalance(netBalance)
                    .build());
        }
        return dtos;
    }

    /**
     * Runs the min-cash-flow debt simplification engine for a group.
     */
    public DebtSimplificationResult simplifyGroupDebts(Long groupId) {
        List<MemberBalanceDto> balances = getMemberBalances(groupId);
        return debtSimplificationService.simplifyDebts(balances);
    }

    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    /* ------------------------- Response mapping ------------------------- */

    private SettlementResponse toResponse(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .payerId(settlement.getPayer().getId())
                .payerName(settlement.getPayer().getName())
                .payeeId(settlement.getPayee().getId())
                .payeeName(settlement.getPayee().getName())
                .amount(settlement.getAmount())
                .notes(settlement.getNotes())
                .settledAt(settlement.getSettledAt())
                .build();
    }
}