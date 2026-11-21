package com.splitease.group;

import com.splitease.group.dto.CreateGroupRequest;
import com.splitease.group.dto.GroupResponse;
import com.splitease.group.dto.JoinGroupRequest;
import com.splitease.common.exception.ApiException;
import com.splitease.user.User;
import com.splitease.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Value("${app.group.invite-code-length:8}")
    private int inviteCodeLength;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        String inviteCode = generateUniqueInviteCode();

        ExpenseGroup group = ExpenseGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .inviteCode(inviteCode)
                .createdBy(currentUser.getId())
                .build();

        group = groupRepository.save(group);

        GroupMember adminMember = GroupMember.builder()
                .groupId(group.getId())
                .userId(currentUser.getId())
                .role(GroupRole.ADMIN)
                .build();

        memberRepository.save(adminMember);

        GroupResponse response = GroupResponse.summary(group, GroupRole.ADMIN, 1);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/join")
    public ResponseEntity<GroupResponse> joinGroup(
            @Valid @RequestBody JoinGroupRequest request,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        ExpenseGroup group = groupRepository.findByInviteCode(request.getInviteCode())
                .orElseThrow(() -> ApiException.notFound("GROUP_NOT_FOUND", "Invalid invite code"));

        if (memberRepository.existsByGroupIdAndUserId(group.getId(), currentUser.getId())) {
            throw ApiException.conflict("ALREADY_MEMBER", "You are already a member of this group");
        }

        GroupMember member = GroupMember.builder()
                .groupId(group.getId())
                .userId(currentUser.getId())
                .role(GroupRole.MEMBER)
                .build();

        memberRepository.save(member);

        List<GroupMember> members = memberRepository.findByGroupId(group.getId());
        List<User> users = userRepository.findAllById(members.stream().map(GroupMember::getUserId).toList());

        GroupResponse response = GroupResponse.from(group, GroupRole.MEMBER, members.size(), members, users);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> getUserGroups(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<ExpenseGroup> groups = groupRepository.findByUserId(currentUser.getId());

        List<GroupResponse> responses = groups.stream()
                .map(group -> {
                    GroupMember member = memberRepository.findByGroupIdAndUserId(group.getId(), currentUser.getId())
                            .orElseThrow();
                    long memberCount = memberRepository.countByGroupId(group.getId());
                    return GroupResponse.summary(group, member.getRole(), (int) memberCount);
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroup(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        ExpenseGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("GROUP_NOT_FOUND", "Group not found"));

        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, currentUser.getId())
                .orElseThrow(() -> ApiException.forbidden("NOT_MEMBER", "You are not a member of this group"));

        List<GroupMember> members = memberRepository.findByGroupId(groupId);
        List<User> users = userRepository.findAllById(members.stream().map(GroupMember::getUserId).toList());

        GroupResponse response = GroupResponse.from(group, member.getRole(), members.size(), members, users);
        return ResponseEntity.ok(response);
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(inviteCodeLength);
            for (int i = 0; i < inviteCodeLength; i++) {
                sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }
}