# OSM Data Report

- generatedAt: 2026-05-20T23:38:13
- targetNameInput: 北京邮电大学
- scenicNameMatched: 北京邮电大学
- query: 北京邮电大学, 杏坛路, 北太平庄街道, 海淀区, 北京市, 100088, 中国
- matchedAddressName: 北京邮电大学, 师大北路, 北太平庄街道, 海淀区, 北京市, 100088, 中国
- outputDirSlug: 北京邮电大学-师大北路-北太平庄街道-海淀区-北京市-100088-中国
- center: 116.351933,39.960227
- overpassMode: area
- placeId: 205728870
- osmAnchor: way/279303636
- scenicCount: 1
- poiCount: 696
- facilityCount: 2
- roadCount: 777
- unmatchedPoiTypeCount: 37

## Notes
- Review-only output. Existing seed files remain unchanged.
- OSM coverage may include nearby non-campus entities within radius; manual approval required.
- roadNetworkNodes: 636
- roadGraphEdgesApprox: 731
- virtualNodeCount: 636
- snappedPoiCount: 60
- businessPoiAttachCount: 60/60 (100.00%)
- isolatedVirtualNodeCount: 2
- degreeOneVirtualNodeCount: 115
- nodeDegreeHistogram: deg1=175, deg2=256, deg3plus=263
- Roads are generated from OSM highway network with virtual non-POI nodes as endpoints.
- unmatchedPoiTypeLog: raw/unmatched_poi_types.json
- contextSource: nominatim

## Payload Size
- scenicAreasBytes: 510
- poisBytes: 171096
- facilitiesBytes: 797
- roadsBytes: 218565
- totalBytes: 390968

## Applied To Seed
- scenic_areas appended: 1
- buildings(POI) appended: 696
- facilities appended: 2
- roads appended: 777

## Map Imports
- updated: true
- mapImportsFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/map-imports.json
- classpathBase: osm-data/北京邮电大学-师大北路-北太平庄街道-海淀区-北京市-100088-中国/latest
- idRegistryFile: D:/Dev/GitRepo/BUPT_PersonalizedTravelRecommendationSystem/src/main/resources/dev-seed/id-registry.json

## Indoor Collection
- enabled: true
- candidates: 12
- ok: 0
- reject: 7
- error: 5
- manifest: src\main\resources\osm-data\北京邮电大学-师大北路-北太平庄街道-海淀区-北京市-100088-中国\latest\indoor\manifest.json
- detailLog: indoor_collect.json
- building 900020851 (档案馆): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020884 (北京邮电大学): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020844 (北京邮电大学出版社): error ['OVERPASS:HTTP Error 504: Gateway Timeout']
- building 900020887 (南区教学楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020886 (宏途书店): error ['OVERPASS:HTTP Error 429: Too Many Requests']
- building 900020873 (教二楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020842 (游泳馆): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020843 (球类馆): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020890 (14号楼): reject ['LEVELS', 'ROOMS', 'CORRIDORS']
- building 900020891 (2号楼): error ['OVERPASS:HTTP Error 429: Too Many Requests']
- building 900020894 (4号楼): error ['OVERPASS:HTTP Error 429: Too Many Requests']
- building 900020888 (7号楼): error ['OVERPASS:HTTP Error 504: Gateway Timeout']