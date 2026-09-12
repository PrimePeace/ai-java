package com.ai.aijava.mapper;

import com.ai.aijava.entity.AuditLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper 接口
 * 继承 MyBatis-Flex 的 BaseMapper，自动获得 CRUD 基础方法。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

}
