package com.ai.aijava.mapper;

import com.ai.aijava.entity.User;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 * 继承 MyBatis-Flex 的 BaseMapper，自动获得 CRUD 基础方法。
 * 复杂查询可在此接口中自定义方法，并在对应的 XML 中编写 SQL。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}