package com.splitease.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroupIdOrderBySettledAtDesc(Long groupId);

    @Query("""
        SELECT s.payer.id, SUM(s.amount)
        FROM Settlement s
        WHERE s.group.id = :groupId
        GROUP BY s.payer.id
        """)
    List<Object[]> totalPaidByMember(@Param("groupId") Long groupId);

    @Query("""
        SELECT s.payee.id, SUM(s.amount)
        FROM Settlement s
        WHERE s.group.id = :groupId
        GROUP BY s.payee.id
        """)
    List<Object[]> totalReceivedByMember(@Param("groupId") Long groupId);
}