# OSM Data Report

- generatedAt: 2026-06-08T18:28:59
- targetNameInput: 北京邮电大学沙河校区
- scenicNameMatched: 北京邮电大学（沙河校区）
- query: 北京邮电大学（沙河校区）, 国脉路, 沙河镇, 昌平区, 北京市, 102206, 中国
- matchedAddressName: 北京邮电大学（沙河校区）, 鸿雁路, 沙河镇, 昌平区, 北京市, 102206, 中国
- outputDirSlug: 北京邮电大学-沙河校区-鸿雁路-沙河镇-昌平区-北京市-102206-中国
- center: 116.283563,40.156116
- overpassMode: area
- placeId: 219438262
- osmAnchor: way/685054417
- scenicCount: 1
- poiCount: 267
- facilityCount: 6
- roadCount: 283
- unmatchedPoiTypeCount: 25

## Notes
- Review-only output. Existing seed files remain unchanged.
- OSM coverage may include nearby non-campus entities within radius; manual approval required.
- roadNetworkNodes: 227
- roadGraphEdgesApprox: 244
- virtualNodeCount: 227
- snappedPoiCount: 40
- businessPoiAttachCount: 40/40 (100.00%)
- isolatedVirtualNodeCount: 0
- degreeOneVirtualNodeCount: 28
- nodeDegreeHistogram: deg1=68, deg2=134, deg3plus=65
- Roads are generated from OSM highway network with virtual non-POI nodes as endpoints.
- unmatchedPoiTypeLog: raw/unmatched_poi_types.json
- contextSource: nominatim

## Payload Size
- scenicAreasBytes: 534
- poisBytes: 69804
- facilitiesBytes: 2118
- roadsBytes: 77065
- totalBytes: 149521

## Map Imports
- updated: true
- mapImportsFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/map-imports.json
- classpathBase: osm-data/北京邮电大学-沙河校区-鸿雁路-沙河镇-昌平区-北京市-102206-中国/latest
- idRegistryFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/id-registry.json

## Indoor Collection
- enabled: true
- candidates: 12
- ok: 3
- reject: 9
- error: 0
- manifest: src\main\resources\osm-data\北京邮电大学-沙河校区-鸿雁路-沙河镇-昌平区-北京市-102206-中国\latest\indoor\manifest.json
- detailLog: indoor_collect.json
- building 900022224 (图书馆): ok
- building 900022239 (公共教学楼): ok
- building 900022222 (北京邮电大学（沙河校区）): reject ['ROOMS', 'CORRIDORS']
- building 900022229 (基建处): reject ['EMPTY_SUBSET']
- building 900022232 (学术报告厅): ok
- building 900022228 (学生活动中心): reject ['EMPTY_SUBSET']
- building 900022227 (教工食堂): reject ['ROOMS', 'CORRIDORS']
- building 900022235 (数字媒体与设计艺术学院楼): reject ['EMPTY_SUBSET']
- building 900022238 (理学院楼): reject ['EMPTY_SUBSET']
- building 900022234 (网络空间安全学院楼 & 现代邮政学院楼): reject ['EMPTY_SUBSET']
- building 900022226 (行政办公楼): reject ['EMPTY_SUBSET']
- building 900022225 (风味食堂): reject ['EMPTY_SUBSET']