export interface MenuNode {
  id: number;
  parentId: number;
  name: string;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  sort: number;
  menuType: "CATALOG" | "MENU" | "BUTTON" | string;
  permission?: string | null;
  visible: number;
  client: "WEB" | "APP" | "ALL" | string;
  status: number;
  children?: MenuNode[];
}

export interface RoleVO {
  id: number;
  code: string;
  name: string;
  status: number;
  remark?: string;
  menuIds: number[];
}

export interface MenuSaveRequest {
  id?: number;
  parentId: number;
  name: string;
  path?: string;
  component?: string;
  icon?: string;
  sort: number;
  menuType: string;
  permission?: string;
  visible: number;
  client: string;
  status: number;
}
