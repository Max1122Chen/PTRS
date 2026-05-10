-- 日记 AIGC 动画成片持久化字段（本站 /media 路径）
ALTER TABLE diaries ADD COLUMN animation_url VARCHAR(768) NULL COMMENT '旅游动画 MP4 本站访问路径';
