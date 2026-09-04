package com.xtong.saas.system.role.controller;

import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.role.dto.AssignRoleMenusDTO;
import com.xtong.saas.system.role.dto.CreateRoleDTO;
import com.xtong.saas.system.role.dto.RoleQueryDTO;
import com.xtong.saas.system.role.dto.UpdateRoleDTO;
import com.xtong.saas.system.role.service.RoleService;
import com.xtong.saas.system.role.vo.RoleVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证角色 Controller 统一结果包装及到领域服务的参数委派。 */
class RoleControllerTest {

    private final RoleService roleService = mock(RoleService.class);
    private final RoleController controller = new RoleController(roleService);

    @Test
    void shouldWrapRoleOperationsAndDelegateArguments() {
        RoleQueryDTO query = new RoleQueryDTO(1, 20, null, null, null);
        PageResult<RoleVO> page = new PageResult<>(List.of(), 0, 1, 20);
        when(roleService.page(query)).thenReturn(page);
        when(roleService.create(new CreateRoleDTO("OPERATOR", "Operator"))).thenReturn("201");

        Result<PageResult<RoleVO>> pageResult = controller.page(query);
        Result<String> createResult = controller.create(new CreateRoleDTO("OPERATOR", "Operator"));
        controller.update(9L, new UpdateRoleDTO("Updated"));
        controller.assignMenus(9L, new AssignRoleMenusDTO(Set.of(2L, 3L)));

        assertThat(pageResult).isEqualTo(Result.success(page));
        assertThat(createResult).isEqualTo(Result.success("201"));
        verify(roleService).update(9L, new UpdateRoleDTO("Updated"));
        verify(roleService).assignMenus(9L, Set.of(2L, 3L));
    }
}
