USE ianct_chinese;
ALTER TABLE relation_annotations
  MODIFY COLUMN relation_type VARCHAR(255) NOT NULL;

-- ALTER TABLE users
--   ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';
-- MySQL不支持 ADD COLUMN IF NOT EXISTS 这种写法，已手动添加

UPDATE users
  SET role = 'ADMIN'
  WHERE username = 'admin';
