package com.xtong.saas.system.user.service;

import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.user.dto.CreateUserDTO;
import com.xtong.saas.system.user.dto.ResetPasswordDTO;
import com.xtong.saas.system.user.dto.UpdateUserDTO;
import com.xtong.saas.system.user.dto.UserQueryDTO;
import com.xtong.saas.system.user.entity.SystemUser;
import com.xtong.saas.system.user.vo.UserVO;

import java.time.LocalDateTime;
import java.util.Set;

/** 定义当前受信租户内用户查询、维护、登录校验和角色分配的领域能力。 */
public interface UserService {

    PageResult<UserVO> page(UserQueryDTO query);

    UserVO get(long userId);

    String create(CreateUserDTO command);

    void update(long userId, UpdateUserDTO command);

    void enable(long userId);

    void disable(long userId, long currentUserId);

    void resetPassword(long userId, ResetPasswordDTO command);

    void delete(long userId, long currentUserId);

    void assignRoles(long userId, Set<Long> roleIds);

    SystemUser requireEnabledForLogin(long tenantId, String username);

    void recordLoginSuccess(long userId, LocalDateTime loginAt);
}
