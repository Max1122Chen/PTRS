# OSM Data Report

- generatedAt: 2026-05-20T23:42:17
- targetNameInput: 贵阳一中
- scenicNameMatched: 贵阳一中
- query: 贵阳一中, 黔灵山路, 金阳街道, 观山湖区, 贵阳市, 贵州省, 550081, 中国
- matchedAddressName: 贵阳一中, 黔灵山路, 金阳街道, 观山湖区, 贵阳市, 贵州省, 550081, 中国
- outputDirSlug: 贵阳一中-黔灵山路-金阳街道-观山湖区-贵阳市-贵州省-550081-中国
- center: 106.623998,26.624311
- overpassMode: around
- placeId: 221406313
- osmAnchor: node/4859449411
- scenicCount: 1
- poiCount: 616
- facilityCount: 0
- roadCount: 632
- unmatchedPoiTypeCount: 26

## Notes
- Review-only output. Existing seed files remain unchanged.
- OSM coverage may include nearby non-campus entities within radius; manual approval required.
- roadNetworkNodes: 565
- roadGraphEdgesApprox: 584
- virtualNodeCount: 565
- snappedPoiCount: 48
- businessPoiAttachCount: 48/51 (94.12%)
- isolatedVirtualNodeCount: 0
- degreeOneVirtualNodeCount: 24
- nodeDegreeHistogram: deg1=72, deg2=459, deg3plus=82
- Roads are generated from OSM highway network with virtual non-POI nodes as endpoints.
- unmatchedPoiTypeLog: raw/unmatched_poi_types.json
- contextSource: nominatim

## Payload Size
- scenicAreasBytes: 506
- poisBytes: 150839
- facilitiesBytes: 2
- roadsBytes: 180044
- totalBytes: 331391

## Applied To Seed
- scenic_areas appended: 1
- buildings(POI) appended: 616
- facilities appended: 0
- roads appended: 632

## Map Imports
- updated: true
- mapImportsFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/map-imports.json
- classpathBase: osm-data/贵阳一中-黔灵山路-金阳街道-观山湖区-贵阳市-贵州省-550081-中国/latest
- idRegistryFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/id-registry.json

## Indoor Collection
- enabled: true
- candidates: 12
- ok: 0
- reject: 8
- error: 4
- manifest: src\main\resources\osm-data\贵阳一中-黔灵山路-金阳街道-观山湖区-贵阳市-贵州省-550081-中国\latest\indoor\manifest.json
- detailLog: indoor_collect.json
- building 900021592 (图书馆): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021634 (华东师范大学附属贵阳学校): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021604 (国际部): error ['OVERPASS:HTTP Error 429: Too Many Requests']
- building 900021601 (国际部食堂): error ['OVERPASS:HTTP Error 504: Gateway Timeout']
- building 900021591 (学生食堂): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021605 (宿管中心): error ['OVERPASS:HTTP Error 504: Gateway Timeout']
- building 900021600 (科技艺术馆): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021595 (综合楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021617 (贵阳一中): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021599 (音乐厅): error ['OVERPASS:HTTP Error 429: Too Many Requests']
- building 900021598 (高一组团): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021594 (高三组团): reject ['LEVELS', 'ROOMS', 'CORRIDORS']