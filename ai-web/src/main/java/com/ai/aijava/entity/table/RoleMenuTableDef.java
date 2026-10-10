package com.ai.aijava.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;

/**
 * role_menu 表定义
 */
public class RoleMenuTableDef extends QueryTable {

    public static final RoleMenuTableDef ROLE_MENU = new RoleMenuTableDef();

    public final QueryColumn ROLE_ID = new QueryColumn(this, "role_id");
    public final QueryColumn MENU_ID = new QueryColumn(this, "menu_id");

    public RoleMenuTableDef() {
        super("", "role_menu");
    }
}
