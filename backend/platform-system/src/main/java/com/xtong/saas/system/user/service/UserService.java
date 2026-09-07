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

    /** 分页查询当前租户内符合条件的用户。 */
    PageResult<UserVO> page(UserQueryDTO query);

    /** 获取当前租户内指定用户的详情。 */
    UserVO get(long userId);

    /** 在当前租户内创建用户并返回用户 ID。 */
    String create(CreateUserDTO command);

    /** 更新当前租户内指定用户的基础资料。 */
    void update(long userId, UpdateUserDTO command);

    /** 启用当前租户内指定用户。 */
    void enable(long userId);

    /** 禁用指定用户，并阻止当前用户禁用自身。 */
    void disable(long userId, long currentUserId);

    /** 重置指定用户密码并使其已有会话失效。 */
    void resetPassword(long userId, ResetPasswordDTO command);

    /** 删除指定用户，并阻止当前用户删除自身。 */
    void delete(long userId, long currentUserId);

    /** 替换指定用户的全部角色授权。 */
    void assignRoles(long userId, Set<Long> roleIds);

    /** 按租户和用户名加载可登录的启用用户。 */
    SystemUser requireEnabledForLogin(long tenantId, String username);

    /** 按租户和用户 ID 加载可继续使用会话的启用用户。 */
    SystemUser requireEnabledForSession(long tenantId, long userId);

    /** 记录指定用户最近一次成功登录时间。 */
    void recordLoginSuccess(long userId, LocalDateTime loginAt);
}
