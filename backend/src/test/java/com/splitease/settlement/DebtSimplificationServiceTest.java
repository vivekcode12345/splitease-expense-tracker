package com.splitease.settlement;

import com.splitease.settlement.dto.DebtSimplificationResult;
import com.splitease.settlement.dto.MemberBalanceDto;
import com.splitease.settlement.dto.SimplifiedTransactionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DebtSimplificationServiceTest {

    private final DebtSimplificationService service = new DebtSimplificationService();

    private MemberBalanceDto b(Long id, String name, BigDecimal net) {
        return MemberBalanceDto.builder().userId(id).name(name).netBalance(net).build();
    }

    @Test
    @DisplayName("Transitive chain: A owes B ₹500, B owes C ₹500 → simplifies to A pays C ₹500")
    void transitiveChainSimplifiesToOneTransaction() {
        List<MemberBalanceDto> balances = List.of(
                b(1L, "A", new BigDecimal("-500.00")),
                b(2L, "B", BigDecimal.ZERO),
                b(3L, "C", new BigDecimal("500.00"))
        );

        DebtSimplificationResult result = service.simplifyDebts(balances);

        assertThat(result.getRawTransactionCount()).isEqualTo(2);
        assertThat(result.getSimplifiedTransactionCount()).isEqualTo(1);
        assertThat(result.getTransactionsSaved()).isEqualTo(1);

        List<SimplifiedTransactionDto> txs = result.getTransactions();
        assertThat(txs).hasSize(1);
        SimplifiedTransactionDto tx = txs.get(0);
        assertThat(tx.getFromUserId()).isEqualTo(1L);
        assertThat(tx.getFromUserName()).isEqualTo("A");
        assertThat(tx.getToUserId()).isEqualTo(3L);
        assertThat(tx.getToUserName()).isEqualTo("C");
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        BigDecimal totalTransferred = txs.stream()
                .map(SimplifiedTransactionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalTransferred).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Multi-party circular debt: A→B→C→A equal amounts → 0 transactions")
    void circularDebtSimplifiesToZeroTransactions() {
        List<MemberBalanceDto> balances = List.of(
                b(1L, "A", BigDecimal.ZERO),
                b(2L, "B", BigDecimal.ZERO),
                b(3L, "C", BigDecimal.ZERO)
        );

        DebtSimplificationResult result = service.simplifyDebts(balances);

        assertThat(result.getRawTransactionCount()).isEqualTo(0);
        assertThat(result.getSimplifiedTransactionCount()).isEqualTo(0);
        assertThat(result.getTransactionsSaved()).isEqualTo(0);
        assertThat(result.getTransactions()).isEmpty();
    }

    @Test
    @DisplayName("Complex 4-person mixed balances with paisa precision → conservation holds")
    void complexFourPersonBalancesConserveMoney() {
        List<MemberBalanceDto> balances = List.of(
                b(1L, "A", new BigDecimal("100.00")),
                b(2L, "B", new BigDecimal("-50.00")),
                b(3L, "C", new BigDecimal("-30.00")),
                b(4L, "D", new BigDecimal("-20.00"))
        );

        DebtSimplificationResult result = service.simplifyDebts(balances);

        assertThat(result.getRawTransactionCount()).isEqualTo(4);
        assertThat(result.getSimplifiedTransactionCount()).isEqualTo(3);
        assertThat(result.getTransactionsSaved()).isEqualTo(1);

        List<SimplifiedTransactionDto> txs = result.getTransactions();

        BigDecimal totalTransferred = txs.stream()
                .map(SimplifiedTransactionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalTransferred).isEqualByComparingTo(new BigDecimal("100.00"));

        for (MemberBalanceDto initial : balances) {
            Long uid = initial.getUserId();
            BigDecimal received = txs.stream()
                    .filter(t -> t.getToUserId().equals(uid))
                    .map(SimplifiedTransactionDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = txs.stream()
                    .filter(t -> t.getFromUserId().equals(uid))
                    .map(SimplifiedTransactionDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal delta = initial.getNetBalance().subtract(received).add(paid);
            assertThat(delta.abs().compareTo(new BigDecimal("0.01"))).isLessThan(0);
        }
    }

    @Test
    @DisplayName("Already settled group (all 0.00 balances) → empty transaction list")
    void alreadySettledGroupReturnsEmpty() {
        List<MemberBalanceDto> balances = List.of(
                b(1L, "A", BigDecimal.ZERO),
                b(2L, "B", new BigDecimal("0.00")),
                b(3L, "C", new BigDecimal("-0.00"))
        );

        DebtSimplificationResult result = service.simplifyDebts(balances);

        assertThat(result.getRawTransactionCount()).isEqualTo(0);
        assertThat(result.getSimplifiedTransactionCount()).isEqualTo(0);
        assertThat(result.getTransactionsSaved()).isEqualTo(0);
        assertThat(result.getTransactions()).isEmpty();
    }

    @Test
    @DisplayName("Balances within 1 paisa are treated as settled and ignored")
    void smallPaisaBalancesAreIgnored() {
        List<MemberBalanceDto> balances = List.of(
                b(1L, "A", new BigDecimal("0.005")),
                b(2L, "B", new BigDecimal("-0.005")),
                b(3L, "C", new BigDecimal("10.00"))
        );

        DebtSimplificationResult result = service.simplifyDebts(balances);

        assertThat(result.getRawTransactionCount()).isEqualTo(1);
        assertThat(result.getSimplifiedTransactionCount()).isEqualTo(0);
        assertThat(result.getTransactions()).isEmpty();
    }
}