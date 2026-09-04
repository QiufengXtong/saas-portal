package com.xtong.saas.system.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xtong.saas.common.model.BaseEntity;
import com.xtong.saas.system.tenant.enums.TenantStatus;
import lombok.Getter;
import lombok.Setter;

/** 表示全平台租户目录中的租户持久化模型。 */
@TableName("sys_tenant")
@Getter
@Setter
public class SystemTenant extends BaseEntity {

    private String tenantCode;
    private String tenantName;
    private TenantStatus status;
}
