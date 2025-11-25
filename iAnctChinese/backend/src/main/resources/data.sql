INSERT INTO texts (id, title, content, category, author, era, created_at, updated_at)
VALUES (1, '赤壁赋', '壬戌之秋，七月既望……', 'travelogue', '苏轼', '宋', NOW(), NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO entity_annotations (id, text_id, start_offset, end_offset, label, category, confidence)
VALUES (1, 1, 0, 3, '苏轼', 'PERSON', 0.98)
ON DUPLICATE KEY UPDATE label = VALUES(label);
