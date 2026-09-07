package com.xtong.saas.system.menu.model;

/** 承载菜单权限变化时需要失效会话的租户用户主键组合。 */
public record MenuAffectedUser(long tenantId, long userId) {
}
