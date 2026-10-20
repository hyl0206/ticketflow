-- CANDIDATE indexes, not performance-validated recommendations.
-- Opt-in MySQL 8.4 experiment only; NOT an automatic migration.
-- Run on a disposable performance database after saving baseline EXPLAIN and timing.
-- Set exactly the candidate(s) under test to 1 in your current connection.
-- Defaults are all 0, so simply running this file does not change the schema.
-- Re-running a selected candidate skips an index with the same name.
-- Existing single-column indexes remain; decide on removals only after measurement.

SET @tf_index_status_assignee = COALESCE(@tf_index_status_assignee, 0);
SET @tf_index_creator_created = COALESCE(@tf_index_creator_created, 0);
SET @tf_index_status_priority_created = COALESCE(@tf_index_status_priority_created, 0);

SET @tf_index_ddl = IF(
    @tf_index_status_assignee = 1 AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'ticket'
          AND index_name = 'idx_ticket_status_assignee'
    ),
    'ALTER TABLE ticket ADD INDEX idx_ticket_status_assignee (status, assignee_id)',
    'SELECT ''Skipped idx_ticket_status_assignee'' AS migration_result'
);
PREPARE tf_index_stmt FROM @tf_index_ddl;
EXECUTE tf_index_stmt;
DEALLOCATE PREPARE tf_index_stmt;

SET @tf_index_ddl = IF(
    @tf_index_creator_created = 1 AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'ticket'
          AND index_name = 'idx_ticket_creator_created'
    ),
    'ALTER TABLE ticket ADD INDEX idx_ticket_creator_created (creator_id, created_at)',
    'SELECT ''Skipped idx_ticket_creator_created'' AS migration_result'
);
PREPARE tf_index_stmt FROM @tf_index_ddl;
EXECUTE tf_index_stmt;
DEALLOCATE PREPARE tf_index_stmt;

SET @tf_index_ddl = IF(
    @tf_index_status_priority_created = 1 AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'ticket'
          AND index_name = 'idx_ticket_status_priority_created'
    ),
    'ALTER TABLE ticket ADD INDEX idx_ticket_status_priority_created (status, priority, created_at)',
    'SELECT ''Skipped idx_ticket_status_priority_created'' AS migration_result'
);
PREPARE tf_index_stmt FROM @tf_index_ddl;
EXECUTE tf_index_stmt;
DEALLOCATE PREPARE tf_index_stmt;

SET @tf_index_status_assignee = 0;
SET @tf_index_creator_created = 0;
SET @tf_index_status_priority_created = 0;
SET @tf_index_ddl = NULL;
