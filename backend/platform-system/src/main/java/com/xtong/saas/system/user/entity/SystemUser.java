package com.xtong.saas.system.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xtong.saas.common.model.BaseEntity;
import com.xtong.saas.system.user.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表示租户内可登录的系统用户持久化模型。 */
@TableName("sys_user")
@Getter
@Setter
public class SystemUser extends BaseEntity {

    private Long tenantId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String mobile;
    private UserStatus status;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime lastLoginAt;
    private Long authVersion;
}
