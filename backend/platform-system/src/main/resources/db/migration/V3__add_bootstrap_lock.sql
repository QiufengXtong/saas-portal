-- 文件作用：V3 创建首租户初始化的数据库级全局互斥哨兵，确保多实例只能串行引导。

CREATE TABLE sys_bootstrap_lock (
    id BIGINT NOT NULL COMMENT '固定互斥哨兵主键',
    PRIMARY KEY (id)
) COMMENT '首租户初始化全局锁';

INSERT INTO sys_bootstrap_lock (id) VALUES (1);
