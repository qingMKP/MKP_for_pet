-- 寄养时间精度升级：date → datetime，days → duration_minutes（分钟）
-- 保留现有数据。执行方式：在 mkp_db 库中执行本脚本。
--
-- 注意：旧数据 days 列存的是「天数」，CHANGE 后只是改名为 duration_minutes，
-- 旧值仍是「天数」语义而非「分钟」，如需精确请手动换算该列或删除旧发布单后重新发布。

ALTER TABLE `mkp_posting`
  MODIFY COLUMN `start_date` datetime DEFAULT NULL COMMENT '寄养开始时间',
  MODIFY COLUMN `end_date` datetime DEFAULT NULL COMMENT '寄养结束时间',
  CHANGE COLUMN `days` `duration_minutes` int DEFAULT NULL COMMENT '寄养时长(分钟)';
