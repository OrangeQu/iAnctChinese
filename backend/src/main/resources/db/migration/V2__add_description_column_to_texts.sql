-- Adds optional description to texts for new document dialog.
ALTER TABLE texts
    ADD COLUMN IF NOT EXISTS description TEXT NULL;
