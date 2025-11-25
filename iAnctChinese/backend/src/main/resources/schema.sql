-- Reference schema for the iAnctChinese platform.

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  create_time DATETIME NOT NULL,
  last_login_time DATETIME,
  enabled BIT DEFAULT 1,
  INDEX idx_username (username),
  INDEX idx_email (email)
);

CREATE TABLE IF NOT EXISTS texts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  category VARCHAR(64) NOT NULL,
  author VARCHAR(255),
  era VARCHAR(255),
  uploaded_by VARCHAR(255),
  created_at DATETIME,
  updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS text_sections (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  text_id BIGINT,
  sequence_index INT NOT NULL,
  original_text TEXT NOT NULL,
  punctuated_text TEXT,
  summary TEXT,
  CONSTRAINT fk_sections_text FOREIGN KEY (text_id) REFERENCES texts (id)
);

CREATE TABLE IF NOT EXISTS entity_annotations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  text_id BIGINT,
  section_id BIGINT,
  start_offset INT NOT NULL,
  end_offset INT NOT NULL,
  label VARCHAR(255) NOT NULL,
  category VARCHAR(64),
  confidence DOUBLE,
  color VARCHAR(32),
  CONSTRAINT fk_entities_text FOREIGN KEY (text_id) REFERENCES texts (id),
  CONSTRAINT fk_entities_section FOREIGN KEY (section_id) REFERENCES text_sections (id)
);

CREATE TABLE IF NOT EXISTS relation_annotations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  text_id BIGINT,
  source_entity_id BIGINT,
  target_entity_id BIGINT,
  relation_type VARCHAR(64),
  confidence DOUBLE,
  evidence TEXT,
  CONSTRAINT fk_relations_text FOREIGN KEY (text_id) REFERENCES texts (id),
  CONSTRAINT fk_relations_source FOREIGN KEY (source_entity_id) REFERENCES entity_annotations (id),
  CONSTRAINT fk_relations_target FOREIGN KEY (target_entity_id) REFERENCES entity_annotations (id)
);

CREATE TABLE IF NOT EXISTS visualization_presets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  label VARCHAR(255) NOT NULL,
  text_category VARCHAR(64) NOT NULL,
  config_json TEXT,
  is_default BIT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS model_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  text_id BIGINT,
  job_type VARCHAR(64),
  status VARCHAR(64),
  payload LONGTEXT,
  result_data LONGTEXT,
  created_at DATETIME,
  completed_at DATETIME
);
