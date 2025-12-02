USE ianct_chinese;
-- 清理旧地图数据
DELETE FROM map_info;

-- 3. 插入地图信息
INSERT INTO map_info (id, dynasty, filename, start_year, end_year, width, height) VALUES 
(1, '秦朝', '秦时期全图.jpg', -221, -207, 2900, 2033),
(2, '唐朝', '唐时期全图（二）.jpg', 618, 907, 2884, 2025),
(3, '三国', '三国时期全图.jpg', 220, 280, 2900, 2031),
(4, '北宋/辽', '辽，北宋时期全图.jpg', 960, 1127, 2908, 2033),
(5, '西汉', '西汉时期全图.jpg', -202, 8, 2908, 2029),
(6, '明朝', '明时期全图（一）.jpg', 1368, 1644, 2908, 2036);