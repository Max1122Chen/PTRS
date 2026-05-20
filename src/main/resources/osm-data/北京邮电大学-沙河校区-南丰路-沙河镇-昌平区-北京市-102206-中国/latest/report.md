# OSM Data Report

- generatedAt: 2026-05-20T23:36:46
- targetNameInput: 北京邮电大学（沙河校区）
- scenicNameMatched: 北京邮电大学（沙河校区）
- query: 北京邮电大学（沙河校区）, 南丰路, 沙河镇, 昌平区, 北京市, 102206, 中国
- matchedAddressName: 北京邮电大学（沙河校区）, 南丰路, 沙河镇, 昌平区, 北京市, 102206, 中国
- outputDirSlug: 北京邮电大学-沙河校区-南丰路-沙河镇-昌平区-北京市-102206-中国
- center: 116.283563,40.156116
- overpassMode: area
- placeId: 218212303
- osmAnchor: way/685054417
- scenicCount: 1
- poiCount: 272
- facilityCount: 1
- roadCount: 288
- unmatchedPoiTypeCount: 27

## Notes
- Review-only output. Existing seed files remain unchanged.
- OSM coverage may include nearby non-campus entities within radius; manual approval required.
- roadNetworkNodes: 227
- roadGraphEdgesApprox: 244
- virtualNodeCount: 227
- snappedPoiCount: 45
- businessPoiAttachCount: 45/45 (100.00%)
- isolatedVirtualNodeCount: 0
- degreeOneVirtualNodeCount: 28
- nodeDegreeHistogram: deg1=73, deg2=133, deg3plus=66
- Roads are generated from OSM highway network with virtual non-POI nodes as endpoints.
- unmatchedPoiTypeLog: raw/unmatched_poi_types.json
- contextSource: nominatim

## Payload Size
- scenicAreasBytes: 534
- poisBytes: 69768
- facilitiesBytes: 348
- roadsBytes: 78388
- totalBytes: 149038

## Applied To Seed
- scenic_areas appended: 1
- buildings(POI) appended: 272
- facilities appended: 1
- roads appended: 288

## Map Imports
- updated: true
- mapImportsFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/map-imports.json
- classpathBase: osm-data/北京邮电大学-沙河校区-南丰路-沙河镇-昌平区-北京市-102206-中国/latest
- idRegistryFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/id-registry.json

## Indoor Collection
- enabled: true
- candidates: 12
- ok: 1
- reject: 8
- error: 3
- manifest: src\main\resources\osm-data\北京邮电大学-沙河校区-南丰路-沙河镇-昌平区-北京市-102206-中国\latest\indoor\manifest.json
- detailLog: indoor_collect.json
- building 900020591 (图书馆): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020582 (图书馆自习室): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020607 (公共教学楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020589 (北京邮电大学（沙河校区）): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020596 (基建处): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020599 (学术报告厅): ok
- building 900020595 (学生活动中心): error ['OVERPASS:HTTP Error 504: Gateway Timeout']
- building 900020594 (教工食堂): error ['OVERPASS:HTTP Error 504: Gateway Timeout']
- building 900020603 (数字媒体与设计艺术学院楼): reject ['CORRIDORS']
- building 900020606 (理学院楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020602 (网络空间安全学院楼 & 现代邮政学院楼): error ['OVERPASS:HTTP Error 429: Too Many Requests']
- building 900020593 (行政办公楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']