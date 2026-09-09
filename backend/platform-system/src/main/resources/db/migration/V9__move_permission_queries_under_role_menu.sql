-- 将授权查询按钮归入角色管理，仅调整层级与排序，保留权限码、范围和已有授权。
UPDATE sys_menu
SET parent_id = 120, sort_order = 9, updated_at = CURRENT_TIMESTAMP
WHERE id = 10001 AND permission_code = 'system:menu:tree';

UPDATE sys_menu
SET parent_id = 120, sort_order = 10, updated_at = CURRENT_TIMESTAMP
WHERE id = 10002 AND permission_code = 'system:permission:list';
