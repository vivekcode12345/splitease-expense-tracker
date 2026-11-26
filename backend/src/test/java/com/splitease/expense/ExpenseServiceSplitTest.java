package com.splitease.expense;

import com.splitease.common.exception.ApiException;
import com.splitease.expense.dto.CreateExpenseRequest;
import com.splitease.expense.dto.CreateExpenseSplitRequest;
import com.splitease.expense.dto.ExpenseResponse;
import com.splitease.group.ExpenseGroup;
import com.splitease.group.ExpenseGroupRepository;
import com.splitease.group.GroupMember;
import com.splitease.group.GroupMemberRepository;
import com.splitease.group.GroupRole;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseServiceSplitTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseSplitRepository expenseSplitRepository;
    @Mock private ExpenseGroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private static final Long GROUP_ID = 1L;
    private static final Long U1 = 101L; // paidBy / current user
    private static final Long U2 = 102L;
    private static final Long U3 = 103L;

    private void mockGroupAndMembers() {
        ExpenseGroup group = ExpenseGroup.builder().id(GROUP_ID).name("Test Group").build();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, U1)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, U2)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, U3)).thenReturn(true);

        GroupMember m1 = GroupMember.builder().id(1L).groupId(GROUP_ID).userId(U1).role(GroupRole.ADMIN).build();
        GroupMember m2 = GroupMember.builder().id(2L).groupId(GROUP_ID).userId(U2).role(GroupRole.MEMBER).build();
        GroupMember m3 = GroupMember.builder().id(3L).groupId(GROUP_ID).userId(U3).role(GroupRole.MEMBER).build();
        when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(m1, m2, m3));

        when(userRepository.getReferenceById(U1)).thenReturn(User.builder().id(U1).name("Alice").email("alice@test.com").build());
        when(userRepository.getReferenceById(U2)).thenReturn(User.builder().id(U2).name("Bob").email("bob@test.com").build());
        when(userRepository.getReferenceById(U3)).thenReturn(User.builder().id(U3).name("Carol").email("carol@test.com").build());

        when(expenseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("EQUAL split ₹100.00 across 3 users → 33.34, 33.33, 33.33, exact sum ₹100.00")
    void equalSplitDistributesRemainderToFirstParticipant() {
        mockGroupAndMembers();

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .description("Dinner")
                .totalAmount(new BigDecimal("100.00"))
                .splitType(com.splitease.expense.SplitType.EQUAL)
                .category("Food")
                .paidBy(U1)
                .splits(List.of())
                .build();

        ExpenseResponse response = expenseService.createExpense(GROUP_ID, U1, request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getSplitType()).isEqualTo(com.splitease.expense.SplitType.EQUAL);
        assertThat(response.getSplits()).hasSize(3);

        List<BigDecimal> amounts = response.getSplits().stream()
                .map(com.splitease.expense.dto.ExpenseSplitResponse::getOwedAmount)
                .toList();
        assertThat(amounts).containsExactlyInAnyOrder(
                new BigDecimal("33.34"),
                new BigDecimal("33.33"),
                new BigDecimal("33.33"));

        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("EXACT split with matching sum → succeeds")
    void exactSplitValidSumSucceeds() {
        mockGroupAndMembers();

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .description("Groceries")
                .totalAmount(new BigDecimal("100.00"))
                .splitType(com.splitease.expense.SplitType.EXACT)
                .category("Food")
                .paidBy(U1)
                .splits(List.of(
                        CreateExpenseSplitRequest.builder().userId(U1).owedAmount(new BigDecimal("50.00")).build(),
                        CreateExpenseSplitRequest.builder().userId(U2).owedAmount(new BigDecimal("30.00")).build(),
                        CreateExpenseSplitRequest.builder().userId(U3).owedAmount(new BigDecimal("20.00")).build()
                ))
                .build();

        ExpenseResponse response = expenseService.createExpense(GROUP_ID, U1, request);

        assertThat(response.getSplits()).hasSize(3);
        List<BigDecimal> amounts = response.getSplits().stream()
                .map(com.splitease.expense.dto.ExpenseSplitResponse::getOwedAmount)
                .toList();
        assertThat(amounts).containsExactlyInAnyOrder(
                new BigDecimal("50.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("EXACT split with mismatched sum → throws ApiException 400")
    void exactSplitMismatchedSumThrows() {
        mockGroupAndMembers();

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .description("Groceries")
                .totalAmount(new BigDecimal("100.00"))
                .splitType(com.splitease.expense.SplitType.EXACT)
                .category("Food")
                .paidBy(U1)
                .splits(List.of(
                        CreateExpenseSplitRequest.builder().userId(U1).owedAmount(new BigDecimal("50.00")).build(),
                        CreateExpenseSplitRequest.builder().userId(U2).owedAmount(new BigDecimal("30.00")).build(),
                        CreateExpenseSplitRequest.builder().userId(U3).owedAmount(new BigDecimal("10.00")).build()
                ))
                .build();

        assertThatThrownBy(() -> expenseService.createExpense(GROUP_ID, U1, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    @DisplayName("PERCENTAGE split 50/30/20 → succeeds with correct amounts")
    void percentageSplitValidSucceeds() {
        mockGroupAndMembers();

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .description("Trip")
                .totalAmount(new BigDecimal("100.00"))
                .splitType(com.splitease.expense.SplitType.PERCENTAGE)
                .category("Travel")
                .paidBy(U1)
                .splits(List.of(
                        CreateExpenseSplitRequest.builder().userId(U1).percentage(new BigDecimal("50.00")).build(),
                        CreateExpenseSplitRequest.builder().userId(U2).percentage(new BigDecimal("30.00")).build(),
                        CreateExpenseSplitRequest.builder().userId(U3).percentage(new BigDecimal("20.00")).build()
                ))
                .build();

        ExpenseResponse response = expenseService.createExpense(GROUP_ID, U1, request);

        assertThat(response.getSplits()).hasSize(3);
        List<BigDecimal> amounts = response.getSplits().stream()
                .map(com.splitease.expense.dto.ExpenseSplitResponse::getOwedAmount)
                .toList();
        assertThat(amounts).containsExactlyInAnyOrder(
                new BigDecimal("50.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00"));

        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("PERCENTAGE split summing to 90% → throws ApiException 400")
    void percentageSplitInvalidSumThrows() {
        mockGroupAndMembers();

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .description("Trip")
                .totalAmount(new BigDecimal("100.00"))
                .splitType(com.splitease.expense.SplitType.PERCENTAGE)
                .category("Travel")
                .paidBy(U1)
                .splits(List.of(
                        CreateExpenseSplitRequest.builder().userId(U1).percentage(new BigDecimal("50.00")).build(),
                        CreateExpenseSplitRequest.builder().userId(U2).percentage(new BigDecimal("40.00")).build()
                ))
                .build();

        assertThatThrownBy(() -> expenseService.createExpense(GROUP_ID, U1, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(400));
    }
}