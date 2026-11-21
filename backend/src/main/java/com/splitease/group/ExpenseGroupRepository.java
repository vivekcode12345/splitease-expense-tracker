package com.splitease.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {
    Optional<ExpenseGroup> findByInviteCode(String inviteCode);

    @Query("SELECT g FROM ExpenseGroup g JOIN GroupMember m ON g.id = m.groupId WHERE m.userId = :userId")
    List<ExpenseGroup> findByUserId(@Param("userId") Long userId);

    boolean existsByInviteCode(String inviteCode);
}