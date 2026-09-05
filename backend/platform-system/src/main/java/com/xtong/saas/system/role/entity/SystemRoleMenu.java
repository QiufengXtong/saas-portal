package com.xtong.saas.system.role.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表示租户角色与全局菜单资源的可物理删除持久化关联。 */
@TableName("sys_role_menu")
@Getter
@Setter
public class SystemRoleMenu {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long roleId;
    private Long menuId;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
