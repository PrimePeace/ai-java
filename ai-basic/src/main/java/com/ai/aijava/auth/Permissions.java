package com.ai.aijava.auth;

/**
 * 接口权限码，与 menu.permission 一致。
 */
public final class Permissions {

    public static final String KB_VIEW = "kb:view";
    public static final String KB_EDIT = "kb:edit";
    public static final String CHAT_USE = "chat:use";
    public static final String PROMPT_USE = "prompt:use";
    public static final String FT_USE = "ft:use";
    public static final String USER_LIST = "user:list";
    public static final String USER_BAN = "user:ban";
    public static final String USER_ASSIGN_ROLE = "user:assign-role";
    public static final String ROLE_VIEW = "role:view";
    public static final String ROLE_ASSIGN_MENU = "role:assign-menu";
    public static final String MENU_VIEW = "menu:view";
    public static final String MENU_EDIT = "menu:edit";
    public static final String AUDIT_VIEW = "audit:view";

    private Permissions() {
    }
}
