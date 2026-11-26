package com.splitease.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndGroupId(Long id, Long groupId);

    @Query("""
        SELECT DISTINCT e FROM Expense e
        LEFT JOIN FETCH e.splits s
        LEFT JOIN FETCH s.user
        WHERE e.group.id = :groupId
        ORDER BY e.createdAt DESC
        """)
    List<Expense> findWithSplitsByGroupId(@Param("groupId") Long groupId);

    @Query("""
        SELECT e.paidBy.id, SUM(e.totalAmount)
        FROM Expense e
        WHERE e.group.id = :groupId
        GROUP BY e.paidBy.id
        """)
    List<Object[]> totalPaidByMember(@Param("groupId") Long groupId);

    @Query("""
        SELECT e.category, SUM(e.totalAmount)
        FROM Expense e
        WHERE e.group.id = :groupId
        GROUP BY e.category
        ORDER BY SUM(e.totalAmount) DESC
        """)
    List<Object[]> categorySummary(@Param("groupId") Long groupId);
}