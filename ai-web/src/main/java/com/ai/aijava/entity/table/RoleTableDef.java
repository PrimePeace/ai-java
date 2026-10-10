package com.ai.aijava.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;

/**
 * role 表定义
 */
public class RoleTableDef extends QueryTable {

    public static final RoleTableDef ROLE = new RoleTableDef();

    public final QueryColumn ID = new QueryColumn(this, "id");
    public final QueryColumn CODE = new QueryColumn(this, "code");
    public final QueryColumn NAME = new QueryColumn(this, "name");
    public final QueryColumn STATUS = new QueryColumn(this, "status");

    public RoleTableDef() {
        super("", "role");
    }
}
