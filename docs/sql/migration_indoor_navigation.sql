-- FR-004-5 室内导航（可选：演示环境以 dev-seed 内存为主）

ALTER TABLE buildings
    ADD COLUMN IF NOT EXISTS indoor_available TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否具备室内图',
    ADD COLUMN IF NOT EXISTS osm_indoor_ref VARCHAR(64) NULL COMMENT 'OSM 建筑引用';

CREATE TABLE IF NOT EXISTS indoor_maps (
    building_poi_id BIGINT NOT NULL PRIMARY KEY COMMENT '建筑 POI ID',
    levels TEXT NOT NULL COMMENT '楼层 JSON',
    source VARCHAR(32) NOT NULL DEFAULT 'osm-overpass',
    completeness_score DOUBLE NOT NULL DEFAULT 0,
    entrance_node_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '室内图元数据';

CREATE TABLE IF NOT EXISTS indoor_nodes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    building_poi_id BIGINT NOT NULL,
    level VARCHAR(16) NOT NULL,
    name VARCHAR(100) NULL,
    node_kind VARCHAR(32) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    linked_poi_id BIGINT NULL,
    osm_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_building_level (building_poi_id, level)
) COMMENT '室内节点';

CREATE TABLE IF NOT EXISTS indoor_edges (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    building_poi_id BIGINT NOT NULL,
    start_node_id BIGINT NOT NULL,
    end_node_id BIGINT NOT NULL,
    edge_kind VARCHAR(16) NOT NULL,
    distance DOUBLE NOT NULL,
    directed TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_building (building_poi_id)
) COMMENT '室内边';
