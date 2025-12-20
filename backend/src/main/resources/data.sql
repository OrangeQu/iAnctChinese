USE ianct_chinese;
ALTER TABLE relation_annotations
  MODIFY COLUMN relation_type VARCHAR(255) NOT NULL;
