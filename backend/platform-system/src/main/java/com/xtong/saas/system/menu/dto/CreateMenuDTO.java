package com.xtong.saas.system.menu.dto;

import com.xtong.saas.system.menu.enums.MenuType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 表示创建目录、菜单或按钮权限资源时提交的字段。 */
public record CreateMenuDTO(
        Long parentId,
        @NotBlank @Size(max = 128) String name,
        @NotNull MenuType type,
        @Size(max = 255) String routePath,
        @Size(max = 255) String component,
        @Size(max = 64) String icon,
        @Size(max = 128)
        @Pattern(regexp = "[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*){2,}", message = "权限码格式不正确")
        String permissionCode,
        @NotNull @Min(0) @Max(999999) Integer sortOrder,
        @NotNull Boolean visible) {
}
