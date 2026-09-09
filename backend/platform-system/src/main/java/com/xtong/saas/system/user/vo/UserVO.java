package com.xtong.saas.system.user.vo;

import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 表示可安全返回给客户端的租户用户视图，所有持久化 ID 均编码为字符串。 */
public record UserVO(
        String id,
        String tenantId,
        String username,
        String displayName,
        String email,
        String mobile,
        UserStatus status,
        boolean platformAdmin,
        List<String> roleIds,
        LocalDateTime passwordChangedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 将用户实体和其关联角色映射为不包含密码摘要的 API 视图。 */
    public static UserVO from(SystemUser user, List<Long> roleIds, boolean platformAdmin) {
        return new UserVO(
                user.getId().toString(),
                user.getTenantId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getMobile(),
                user.getStatus(),
                platformAdmin,
                roleIds.stream().map(String::valueOf).toList(),
                user.getPasswordChangedAt(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
