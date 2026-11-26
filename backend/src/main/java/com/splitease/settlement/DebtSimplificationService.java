package com.splitease.settlement;

import com.splitease.settlement.dto.DebtSimplificationResult;
import com.splitease.settlement.dto.MemberBalanceDto;
import com.splitease.settlement.dto.SimplifiedTransactionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

@Service
@RequiredArgsConstructor
public class DebtSimplificationService {

    private static final BigDecimal ONE_PAISA = new BigDecimal("0.01");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Pure, stateless min-cash-flow debt simplification.
     *
     * @param balances each group member's net balance (positive = creditor, negative = debtor)
     * @return simplification result with transactions and comparison summary
     */
    public DebtSimplificationResult simplifyDebts(List<MemberBalanceDto> balances) {
        // Ignore members already settled within 1 paisa
        List<MemberBalanceDto> active = balances.stream()
                .filter(b -> b.getNetBalance() != null
                        && b.getNetBalance().abs().compareTo(ONE_PAISA) >= 0)
                .toList();

        // Max-heap for creditors: descending by remaining positive credit
        PriorityQueue<MemberBalanceDto> maxCreditors = new PriorityQueue<>(
                (a, b) -> b.getNetBalance().compareTo(a.getNetBalance())
        );
        // Max-heap for debtors: descending by absolute owed debt
        PriorityQueue<MemberBalanceDto> maxDebtors = new PriorityQueue<>(
                (a, b) -> b.getNetBalance().abs().compareTo(a.getNetBalance().abs())
        );

        for (MemberBalanceDto b : active) {
            if (b.getNetBalance().compareTo(ZERO) > 0) {
                maxCreditors.offer(b);
            } else {
                maxDebtors.offer(b);
            }
        }

        List<SimplifiedTransactionDto> transactions = new ArrayList<>();
        BigDecimal totalTransferred = ZERO;

        while (!maxCreditors.isEmpty() && !maxDebtors.isEmpty()) {
            MemberBalanceDto creditor = maxCreditors.poll();
            MemberBalanceDto debtor = maxDebtors.poll();

            BigDecimal transferAmount = creditor.getNetBalance()
                    .min(debtor.getNetBalance().abs())
                    .setScale(2, RoundingMode.HALF_UP);

            if (transferAmount.compareTo(ZERO) <= 0) {
                break;
            }

            transactions.add(SimplifiedTransactionDto.builder()
                    .fromUserId(debtor.getUserId())
                    .fromUserName(debtor.getName())
                    .toUserId(creditor.getUserId())
                    .toUserName(creditor.getName())
                    .amount(transferAmount)
                    .build());

            totalTransferred = totalTransferred.add(transferAmount);

            BigDecimal creditorRemaining = creditor.getNetBalance().subtract(transferAmount);
            BigDecimal debtorRemaining = debtor.getNetBalance().abs().subtract(transferAmount);

            if (creditorRemaining.compareTo(ONE_PAISA) >= 0) {
                maxCreditors.offer(new MemberBalanceDto(
                        creditor.getUserId(), creditor.getName(), creditorRemaining));
            }
            if (debtorRemaining.compareTo(ONE_PAISA) >= 0) {
                maxDebtors.offer(new MemberBalanceDto(
                        debtor.getUserId(), debtor.getName(), debtorRemaining.negate()));
            }
        }

        int rawCount = active.size();
        int simplifiedCount = transactions.size();
        int transactionsSaved = Math.max(0, rawCount - simplifiedCount);

        return DebtSimplificationResult.builder()
                .rawTransactionCount(rawCount)
                .simplifiedTransactionCount(simplifiedCount)
                .transactionsSaved(transactionsSaved)
                .transactions(transactions)
                .build();
    }
}