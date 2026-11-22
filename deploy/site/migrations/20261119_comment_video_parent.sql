-- Run once against an existing business database after checking the index does not already exist.
-- New databases receive this index from business-schema.sql.
ALTER TABLE `comment` ADD INDEX `idx_comment_video_parent` (`video_id`, `parent_comment_id`);
