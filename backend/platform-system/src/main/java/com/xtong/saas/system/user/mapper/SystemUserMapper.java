package com.xtong.saas.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.user.entity.SystemUser;
import org.apache.ibatis.annotations.Mapper;

/** 提供租户系统用户的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {
}
