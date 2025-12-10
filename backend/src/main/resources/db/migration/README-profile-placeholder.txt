If your environment uses Flyway/Liquibase, add a migration to create the
`description` column on `texts` (VARCHAR(2048) NULL). This placeholder file is
added to avoid accidental production mismatches; remove it when a real migration
is added.
