package com.splitease.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

    List<ExpenseSplit> findByExpenseId(Long expenseId);

    Optional<ExpenseSplit> findByExpenseIdAndUserId(Long expenseId, Long userId);

    @Query("""
        SELECT s.user.id, SUM(s.owedAmount)
        FROM ExpenseSplit s
        WHERE s.expense.group.id = :groupId
        GROUP BY s.user.id
        """)
    List<Object[]> totalOwedByMember(@Param("groupId") Long groupId);
}