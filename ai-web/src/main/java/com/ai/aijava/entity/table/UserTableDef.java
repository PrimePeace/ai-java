package com.ai.aijava.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;

/**
 * user 表定义
 * 由 MyBatis-Flex 代码生成器生成，请勿手动修改
 */
public class UserTableDef extends QueryTable {

    public static final UserTableDef USER = new UserTableDef();

    public final QueryColumn ID = new QueryColumn(this, "id");
    public final QueryColumn USERNAME = new QueryColumn(this, "username");
    public final QueryColumn PASSWORD = new QueryColumn(this, "password");
    public final QueryColumn NICKNAME = new QueryColumn(this, "nickname");
    public final QueryColumn EMAIL = new QueryColumn(this, "email");
    public final QueryColumn PHONE = new QueryColumn(this, "phone");
    public final QueryColumn LOGIN_IP = new QueryColumn(this, "login_ip");
    public final QueryColumn LOGIN_TIME = new QueryColumn(this, "login_time");
    public final QueryColumn LOGIN_FAIL_COUNT = new QueryColumn(this, "login_fail_count");
    public final QueryColumn LOCK_TIME = new QueryColumn(this, "lock_time");
    public final QueryColumn STATUS = new QueryColumn(this, "status");
    public final QueryColumn ROLE_ID = new QueryColumn(this, "role_id");
    public final QueryColumn REFRESH_INVALID_BEFORE = new QueryColumn(this, "refresh_invalid_before");
    public final QueryColumn CREATE_TIME = new QueryColumn(this, "create_time");
    public final QueryColumn UPDATE_TIME = new QueryColumn(this, "update_time");

    public UserTableDef() {
        super("", "user");
    }

    public UserTableDef(String alias) {
        super("", "user");
        this.setAlias(alias);
    }
}
