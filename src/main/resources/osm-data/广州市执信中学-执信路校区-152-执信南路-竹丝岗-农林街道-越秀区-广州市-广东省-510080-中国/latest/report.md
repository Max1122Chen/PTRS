# OSM Data Report

- generatedAt: 2026-05-20T23:40:36
- targetNameInput: 广州市执信中学(执信路校区)
- scenicNameMatched: 广州市执信中学(执信路校区)
- query: 广州市执信中学(执信路校区), 152, 执信南路, 竹丝岗, 农林街道, 越秀区, 广州市, 广东省, 510080, 中国
- matchedAddressName: 广州市执信中学(执信路校区), 152, 执信南路, 竹丝岗, 农林街道, 越秀区, 广州市, 广东省, 510080, 中国
- outputDirSlug: 广州市执信中学-执信路校区-152-执信南路-竹丝岗-农林街道-越秀区-广州市-广东省-510080-中国
- center: 113.288709,23.135152
- overpassMode: area
- placeId: 220261595
- osmAnchor: way/408006153
- scenicCount: 1
- poiCount: 53
- facilityCount: 1
- roadCount: 55
- unmatchedPoiTypeCount: 3

## Notes
- Review-only output. Existing seed files remain unchanged.
- OSM coverage may include nearby non-campus entities within radius; manual approval required.
- roadNetworkNodes: 32
- roadGraphEdgesApprox: 34
- virtualNodeCount: 32
- snappedPoiCount: 21
- businessPoiAttachCount: 21/21 (100.00%)
- isolatedVirtualNodeCount: 0
- degreeOneVirtualNodeCount: 1
- nodeDegreeHistogram: deg1=22, deg2=16, deg3plus=15
- Roads are generated from OSM highway network with virtual non-POI nodes as endpoints.
- unmatchedPoiTypeLog: raw/unmatched_poi_types.json
- contextSource: nominatim

## Payload Size
- scenicAreasBytes: 571
- poisBytes: 15298
- facilitiesBytes: 361
- roadsBytes: 14582
- totalBytes: 30812

## Applied To Seed
- scenic_areas appended: 1
- buildings(POI) appended: 53
- facilities appended: 1
- roads appended: 55

## Map Imports
- updated: true
- mapImportsFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/map-imports.json
- classpathBase: osm-data/广州市执信中学-执信路校区-152-执信南路-竹丝岗-农林街道-越秀区-广州市-广东省-510080-中国/latest
- idRegistryFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/id-registry.json

## Indoor Collection
- enabled: true
- candidates: 12
- ok: 0
- reject: 9
- error: 3
- manifest: src\main\resources\osm-data\广州市执信中学-执信路校区-152-执信南路-竹丝岗-农林街道-越秀区-广州市-广东省-510080-中国\latest\indoor\manifest.json
- detailLog: indoor_collect.json
- building 900021534 (仁爱楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021547 (以升楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021536 (元培楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021535 (厚德楼): error ['OVERPASS:HTTP Error 429: Too Many Requests']
- building 900021546 (奉恩堂): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021532 (广州市执信中学(执信路校区)): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021533 (执信楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021540 (承志楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021538 (执信女生宿舍): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021539 (执信男生宿舍): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900021537 (中山大学附属眼科医院): error ['OVERPASS:HTTP Error 504: Gateway Timeout']
- building 900021545 (厚望广场): error ['OVERPASS:HTTP Error 504: Gateway Timeout']