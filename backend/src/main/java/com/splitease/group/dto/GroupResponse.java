package com.splitease.group.dto;

import com.splitease.group.ExpenseGroup;
import com.splitease.group.GroupMember;
import com.splitease.group.GroupRole;
import com.splitease.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private String inviteCode;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private int memberCount;
    private GroupRole userRole;
    private List<MemberInfo> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private Long userId;
        private String name;
        private String email;
        private GroupRole role;
        private Instant joinedAt;
    }

    public static GroupResponse from(ExpenseGroup group, GroupRole userRole, int memberCount, List<GroupMember> members, List<User> users) {
        List<MemberInfo> memberInfos = members.stream()
                .map(member -> {
                    User user = users.stream()
                            .filter(u -> u.getId().equals(member.getUserId()))
                            .findFirst()
                            .orElse(null);
                    return MemberInfo.builder()
                            .userId(member.getUserId())
                            .name(user != null ? user.getName() : "Unknown")
                            .email(user != null ? user.getEmail() : "Unknown")
                            .role(member.getRole())
                            .joinedAt(member.getJoinedAt())
                            .build();
                })
                .toList();

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .inviteCode(group.getInviteCode())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .memberCount(memberCount)
                .userRole(userRole)
                .members(memberInfos)
                .build();
    }

    public static GroupResponse summary(ExpenseGroup group, GroupRole userRole, int memberCount) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .inviteCode(group.getInviteCode())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .memberCount(memberCount)
                .userRole(userRole)
                .build();
    }
}