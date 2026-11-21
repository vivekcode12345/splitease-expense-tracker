package com.splitease.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    @Query("SELECT m FROM GroupMember m WHERE m.groupId = :groupId")
    List<GroupMember> findByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT m FROM GroupMember m WHERE m.userId = :userId")
    List<GroupMember> findByUserId(@Param("userId") Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupId(Long groupId);
}