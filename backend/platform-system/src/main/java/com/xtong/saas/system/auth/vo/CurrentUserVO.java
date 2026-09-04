package com.xtong.saas.system.auth.vo;

import java.util.Set;

/** 返回当前有效会话中的用户身份、显示名称和权限快照。 */
public record CurrentUserVO(
        String tenantId,
        String userId,
        String username,
        String displayName,
        Set<String> permissions) {

    /** 固化权限集合，避免响应对象被调用方后续修改。 */
    public CurrentUserVO {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
