package com.splitease.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinGroupRequest {
    @NotBlank(message = "Invite code is required")
    @Size(min = 8, max = 8, message = "Invite code must be 8 characters")
    private String inviteCode;
}