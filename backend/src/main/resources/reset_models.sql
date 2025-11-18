-- 清空旧的模型配置，让应用重新初始化
-- 使用前请先备份数据！
-- 执行方式：在MySQL客户端执行此脚本，或在应用启动前手动执行

USE ianct_chinese;

-- 删除所有模型配置
TRUNCATE TABLE model_configs;

-- 应用重启后会自动重新初始化模型配置
