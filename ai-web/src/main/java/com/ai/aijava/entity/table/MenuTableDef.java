package com.ai.aijava.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;

/**
 * menu 表定义
 */
public class MenuTableDef extends QueryTable {

    public static final MenuTableDef MENU = new MenuTableDef();

    public final QueryColumn ID = new QueryColumn(this, "id");
    public final QueryColumn PARENT_ID = new QueryColumn(this, "parent_id");
    public final QueryColumn STATUS = new QueryColumn(this, "status");
    public final QueryColumn SORT = new QueryColumn(this, "sort");
    public final QueryColumn CLIENT = new QueryColumn(this, "client");
    public final QueryColumn MENU_TYPE = new QueryColumn(this, "menu_type");

    public MenuTableDef() {
        super("", "menu");
    }
}
