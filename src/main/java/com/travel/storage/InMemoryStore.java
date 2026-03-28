package com.travel.storage;

import com.travel.model.entity.Comment;
import com.travel.model.entity.Building;
import com.travel.model.entity.Diary;
import com.travel.model.entity.DiaryDestination;
import com.travel.model.entity.Facility;
import com.travel.model.entity.Food;
import com.travel.model.entity.Road;
import com.travel.model.entity.Restaurant;
import com.travel.model.entity.ScenicArea;
import com.travel.model.entity.ScenicAreaTag;
import com.travel.model.entity.Tag;
import com.travel.model.entity.User;
import com.travel.model.entity.UserBehavior;
import com.travel.model.entity.UserInterest;
import org.springframework.stereotype.Component;
import com.travel.storage.search.NGramInvertedIndex;
import com.travel.storage.search.PrefixTrieIdIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局 In-Memory 存储容器。
 *
 * <p>
 * 数据在启动阶段由 {@code InMemoryDataLoader} 从数据库预加载并写入本容器。
 * 运行期所有 CRUD 与检索均应只读写本内存结构，不再依赖数据库 Mapper。
 * </p>
 */
@Component
public class InMemoryStore
{

    // ------------------- Users -------------------

    private final Map<Long, User> usersById = new HashMap<>();

    private final Map<String, Long> userIdByUsername = new HashMap<>();

    private final Map<String, Long> userIdByEmail = new HashMap<>();

    private long nextUserId = 1;

    // userId -> (interestType -> weight)
    private final Map<Long, Map<String, Double>> userInterestsByUserId = new HashMap<>();

    private final Map<Long, List<Long>> userInterestIdsByUserId = new HashMap<>();

    // interestId -> UserInterest
    private final Map<Long, UserInterest> userInterestsById = new HashMap<>();

    private long nextUserInterestId = 1;

    private final Map<Long, UserBehavior> userBehaviorsById = new HashMap<>();

    private final Map<Long, List<Long>> behaviorIdsByUserId = new HashMap<>();

    private long nextUserBehaviorId = 1;

    // ------------------- ScenicAreas -------------------

    private final Map<Long, ScenicArea> scenicAreasById = new HashMap<>();

    private final Map<String, List<Long>> scenicAreaIdsByType = new HashMap<>();

    private long nextScenicAreaId = 1;

    // ------------------- Buildings -------------------

    private final Map<Long, Building> buildingsById = new HashMap<>();

    private final Map<Long, List<Long>> buildingIdsByAreaId = new HashMap<>();

    private long nextBuildingId = 1;

    // Tag
    private final Map<Long, Tag> tagsById = new HashMap<>();

    private long nextTagId = 1;

    // ScenicAreaTag table（原始关系）
    private final Map<Long, ScenicAreaTag> scenicAreaTagsById = new HashMap<>();

    private long nextScenicAreaTagId = 1;

    /**
     * 便于个性化推荐：scenicAreaId -> (tagName -> weight)
     */
    private final Map<Long, Map<String, Double>> scenicAreaTagWeightsByScenicAreaId = new HashMap<>();

    // ------------------- Roads / Graph -------------------

    private final Map<Long, Road> roadsById = new HashMap<>();

    private final Map<Long, List<Long>> roadIdsByAreaId = new HashMap<>();

    private long nextRoadId = 1;

    // ------------------- Facilities -------------------

    private final Map<Long, Facility> facilitiesById = new HashMap<>();

    private final Map<Long, List<Long>> facilityIdsByAreaId = new HashMap<>();

    private final Map<String, List<Long>> facilityIdsByType = new HashMap<>();

    private long nextFacilityId = 1;

    // ------------------- Search Indices -------------------

    private PrefixTrieIdIndex facilityPrefixTrieIndex = new PrefixTrieIdIndex();

    private PrefixTrieIdIndex foodPrefixTrieIndex = new PrefixTrieIdIndex();

    private NGramInvertedIndex facilityNGramIndex = new NGramInvertedIndex();

    private NGramInvertedIndex foodNGramIndex = new NGramInvertedIndex();

    private NGramInvertedIndex diaryFullTextIndex = new NGramInvertedIndex();

    // ------------------- Restaurants / Foods -------------------

    private final Map<Long, Restaurant> restaurantsById = new HashMap<>();

    private final Map<Long, List<Long>> restaurantIdsByAreaId = new HashMap<>();

    private long nextRestaurantId = 1;

    private final Map<Long, Food> foodsById = new HashMap<>();

    private final Map<Long, List<Long>> foodIdsByAreaId = new HashMap<>();

    private long nextFoodId = 1;

    // ------------------- Diaries -------------------

    private final Map<Long, Diary> diariesById = new HashMap<>();

    private long nextDiaryId = 1;

    private final Map<Long, List<Long>> diaryDestinationIdsByDiaryId = new HashMap<>();

    private final Map<Long, List<Long>> diaryIdsByDestinationId = new HashMap<>();

    private final Map<Long, DiaryDestination> diaryDestinationsById = new HashMap<>();

    private long nextDiaryDestinationId = 1;

    // ------------------- Comments / Ratings -------------------

    private final Map<Long, Comment> commentsById = new HashMap<>();

    private long nextCommentId = 1;

    /**
     * 简化聚合：targetType#targetId -> Agg(sum,count)
     */
    private final Map<String, RatingAgg> ratingAggByTargetKey = new HashMap<>();

    // ------------------- Public APIs (CRUD + indexes) -------------------

    public synchronized User insertUser(User user)
    {
        Long id = user.getId();
        if (id == null)
        {
            id = nextUserId++;
            user.setId(id);
        }
        usersById.put(id, user);
        if (user.getUsername() != null)
        {
            userIdByUsername.put(user.getUsername(), id);
        }
        if (user.getEmail() != null)
        {
            userIdByEmail.put(user.getEmail(), id);
        }
        nextUserId = Math.max(nextUserId, id + 1);
        return user;
    }

    public User findUserByUsername(String username)
    {
        Long id = userIdByUsername.get(username);
        if (id == null)
        {
            return null;
        }
        return usersById.get(id);
    }

    public User findUserByEmail(String email)
    {
        Long id = userIdByEmail.get(email);
        if (id == null)
        {
            return null;
        }
        return usersById.get(id);
    }

    public User findUserById(Long id)
    {
        return usersById.get(id);
    }

    public synchronized void replaceUserInterests(Long userId, List<UserInterest> interests)
    {
        // 重置聚合映射
        userInterestsByUserId.remove(userId);
        userInterestIdsByUserId.remove(userId);

        List<Long> idList = new ArrayList<>();
        Map<String, Double> weights = new HashMap<>();
        for (UserInterest interest : interests)
        {
            Long iid = interest.getId();
            if (iid == null)
            {
                iid = nextUserInterestId++;
                interest.setId(iid);
            }
            userInterestsById.put(iid, interest);
            idList.add(iid);

            String type = interest.getInterestType();
            if (type != null)
            {
                weights.put(type, interest.getWeight() == null ? 1.0 : interest.getWeight());
            }
            nextUserInterestId = Math.max(nextUserInterestId, iid + 1);
        }
        userInterestIdsByUserId.put(userId, idList);
        userInterestsByUserId.put(userId, weights);
    }

    public Map<String, Double> getUserInterests(Long userId)
    {
        return userInterestsByUserId.get(userId);
    }

    public synchronized UserBehavior insertUserBehavior(UserBehavior behavior)
    {
        Long id = behavior.getId();
        if (id == null)
        {
            id = nextUserBehaviorId++;
            behavior.setId(id);
        }
        userBehaviorsById.put(id, behavior);
        behaviorIdsByUserId.computeIfAbsent(behavior.getUserId(), k -> new ArrayList<>()).add(id);
        nextUserBehaviorId = Math.max(nextUserBehaviorId, id + 1);
        return behavior;
    }

    public List<UserBehavior> getUserBehaviors(Long userId)
    {
        List<Long> ids = behaviorIdsByUserId.get(userId);
        if (ids == null)
        {
            return List.of();
        }
        List<UserBehavior> result = new ArrayList<>(ids.size());
        for (Long id : ids)
        {
            UserBehavior behavior = userBehaviorsById.get(id);
            if (behavior != null)
            {
                result.add(behavior);
            }
        }
        return result;
    }

    public synchronized ScenicArea insertScenicArea(ScenicArea scenicArea)
    {
        Long id = scenicArea.getId();
        if (id == null)
        {
            id = nextScenicAreaId++;
            scenicArea.setId(id);
        }
        scenicAreasById.put(id, scenicArea);

        if (scenicArea.getType() != null)
        {
            scenicAreaIdsByType.computeIfAbsent(scenicArea.getType(), k -> new ArrayList<>()).add(id);
        }
        nextScenicAreaId = Math.max(nextScenicAreaId, id + 1);
        return scenicArea;
    }

    public ScenicArea findScenicAreaById(Long id)
    {
        return scenicAreasById.get(id);
    }

    public synchronized Building insertBuilding(Building building)
    {
        Long id = building.getId();
        if (id == null)
        {
            id = nextBuildingId++;
            building.setId(id);
        }
        buildingsById.put(id, building);
        if (building.getAreaId() != null)
        {
            buildingIdsByAreaId.computeIfAbsent(building.getAreaId(), k -> new ArrayList<>()).add(id);
        }
        nextBuildingId = Math.max(nextBuildingId, id + 1);
        return building;
    }

    public List<ScenicArea> findScenicAreasByType(String type)
    {
        List<Long> ids = scenicAreaIdsByType.get(type);
        if (ids == null)
        {
            return List.of();
        }
        List<ScenicArea> result = new ArrayList<>(ids.size());
        for (Long id : ids)
        {
            ScenicArea sa = scenicAreasById.get(id);
            if (sa != null)
            {
                result.add(sa);
            }
        }
        return result;
    }

    public List<ScenicArea> findAllScenicAreas()
    {
        return new ArrayList<>(scenicAreasById.values());
    }

    public synchronized void indexScenicAreaTagWeights(List<ScenicAreaTag> relations, Map<Long, Tag> tagsById)
    {
        scenicAreaTagWeightsByScenicAreaId.clear();
        for (ScenicAreaTag rel : relations)
        {
            ScenicAreaTag existing = rel;
            Long scenicAreaId = existing.getScenicAreaId();
            Tag tag = tagsById.get(existing.getTagId());
            if (tag == null || tag.getName() == null)
            {
                continue;
            }
            scenicAreaTagWeightsByScenicAreaId
                .computeIfAbsent(scenicAreaId, k -> new HashMap<>())
                .put(tag.getName(), existing.getWeight() == null ? 1.0 : existing.getWeight());
        }
    }

    public synchronized Tag insertTag(Tag tag)
    {
        Long id = tag.getId();
        if (id == null)
        {
            id = nextTagId++;
            tag.setId(id);
        }
        tagsById.put(id, tag);
        nextTagId = Math.max(nextTagId, id + 1);
        return tag;
    }

    public Tag findTagById(Long id)
    {
        return tagsById.get(id);
    }

    public Map<Long, Tag> getTagsById()
    {
        return tagsById;
    }

    public synchronized ScenicAreaTag insertScenicAreaTag(ScenicAreaTag relation)
    {
        Long id = relation.getId();
        if (id == null)
        {
            id = nextScenicAreaTagId++;
            relation.setId(id);
        }
        scenicAreaTagsById.put(id, relation);
        nextScenicAreaTagId = Math.max(nextScenicAreaTagId, id + 1);
        return relation;
    }

    public List<ScenicAreaTag> getAllScenicAreaTags()
    {
        return new ArrayList<>(scenicAreaTagsById.values());
    }

    public void rebuildScenicAreaTagWeights()
    {
        indexScenicAreaTagWeights(getAllScenicAreaTags(), tagsById);
    }

    public Map<String, Double> getScenicAreaTagWeights(Long scenicAreaId)
    {
        return scenicAreaTagWeightsByScenicAreaId.get(scenicAreaId);
    }

    public List<String> getScenicAreaTagNames(Long scenicAreaId)
    {
        Map<String, Double> tagWeights = scenicAreaTagWeightsByScenicAreaId.get(scenicAreaId);
        if (tagWeights == null || tagWeights.isEmpty())
        {
            return List.of();
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(tagWeights.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue() == null ? 0.0 : b.getValue(), a.getValue() == null ? 0.0 : a.getValue()));
        List<String> tags = new ArrayList<>(entries.size());
        for (Map.Entry<String, Double> entry : entries)
        {
            if (entry.getKey() != null && !entry.getKey().isBlank())
            {
                tags.add(entry.getKey());
            }
        }
        return tags;
    }

    public synchronized Road insertRoad(Road road)
    {
        Long id = road.getId();
        if (id == null)
        {
            id = nextRoadId++;
            road.setId(id);
        }
        roadsById.put(id, road);
        if (road.getAreaId() != null)
        {
            roadIdsByAreaId.computeIfAbsent(road.getAreaId(), k -> new ArrayList<>()).add(id);
        }
        nextRoadId = Math.max(nextRoadId, id + 1);
        return road;
    }

    public List<Road> findRoadsByAreaId(Long areaId)
    {
        if (areaId == null)
        {
            return new ArrayList<>(roadsById.values());
        }
        List<Long> ids = roadIdsByAreaId.get(areaId);
        if (ids == null)
        {
            return List.of();
        }
        List<Road> result = new ArrayList<>(ids.size());
        for (Long id : ids)
        {
            Road road = roadsById.get(id);
            if (road != null)
            {
                result.add(road);
            }
        }
        return result;
    }

    public synchronized Facility insertFacility(Facility facility)
    {
        Long id = facility.getId();
        if (id == null)
        {
            id = nextFacilityId++;
            facility.setId(id);
        }
        facilitiesById.put(id, facility);

        if (facility.getAreaId() != null)
        {
            facilityIdsByAreaId.computeIfAbsent(facility.getAreaId(), k -> new ArrayList<>()).add(id);
        }
        if (facility.getType() != null)
        {
            facilityIdsByType.computeIfAbsent(facility.getType(), k -> new ArrayList<>()).add(id);
        }
        nextFacilityId = Math.max(nextFacilityId, id + 1);
        return facility;
    }

    public Facility findFacilityById(Long id)
    {
        return facilitiesById.get(id);
    }

    public List<Facility> findFacilitiesByAreaIdAndType(Long areaId, String type)
    {
        List<Facility> result = new ArrayList<>();
        if (areaId == null)
        {
            for (Facility f : facilitiesById.values())
            {
                if (type == null || type.isBlank() || type.equals(f.getType()))
                {
                    result.add(f);
                }
            }
            return result;
        }

        List<Long> areaIds = facilityIdsByAreaId.get(areaId);
        if (areaIds == null)
        {
            return List.of();
        }

        for (Long id : areaIds)
        {
            Facility f = facilitiesById.get(id);
            if (f == null)
            {
                continue;
            }
            if (type == null || type.isBlank() || type.equals(f.getType()))
            {
                result.add(f);
            }
        }
        return result;
    }

    public synchronized Restaurant insertRestaurant(Restaurant restaurant)
    {
        Long id = restaurant.getId();
        if (id == null)
        {
            id = nextRestaurantId++;
            restaurant.setId(id);
        }
        restaurantsById.put(id, restaurant);
        if (restaurant.getAreaId() != null)
        {
            restaurantIdsByAreaId.computeIfAbsent(restaurant.getAreaId(), k -> new ArrayList<>()).add(id);
        }
        nextRestaurantId = Math.max(nextRestaurantId, id + 1);
        return restaurant;
    }

    public Restaurant findRestaurantById(Long id)
    {
        return restaurantsById.get(id);
    }

    public synchronized Food insertFood(Food food)
    {
        Long id = food.getId();
        if (id == null)
        {
            id = nextFoodId++;
            food.setId(id);
        }
        foodsById.put(id, food);
        if (food.getAreaId() != null)
        {
            foodIdsByAreaId.computeIfAbsent(food.getAreaId(), k -> new ArrayList<>()).add(id);
        }
        nextFoodId = Math.max(nextFoodId, id + 1);
        return food;
    }

    public Food findFoodById(Long id)
    {
        return foodsById.get(id);
    }

    public synchronized void updateFood(Food food)
    {
        if (food == null || food.getId() == null)
        {
            return;
        }
        foodsById.put(food.getId(), food);
    }

    public List<Food> findFoodsByAreaId(Long areaId)
    {
        if (areaId == null)
        {
            return List.of();
        }
        List<Long> ids = foodIdsByAreaId.get(areaId);
        if (ids == null)
        {
            return List.of();
        }
        List<Food> result = new ArrayList<>(ids.size());
        for (Long id : ids)
        {
            Food f = foodsById.get(id);
            if (f != null)
            {
                result.add(f);
            }
        }
        return result;
    }

    public List<Restaurant> findRestaurantsByAreaId(Long areaId)
    {
        if (areaId == null)
        {
            return new ArrayList<>(restaurantsById.values());
        }
        List<Long> ids = restaurantIdsByAreaId.get(areaId);
        if (ids == null)
        {
            return List.of();
        }
        List<Restaurant> result = new ArrayList<>(ids.size());
        for (Long id : ids)
        {
            Restaurant r = restaurantsById.get(id);
            if (r != null)
            {
                result.add(r);
            }
        }
        return result;
    }

    public List<Facility> findAllFacilities()
    {
        return new ArrayList<>(facilitiesById.values());
    }

    public List<Food> findAllFoods()
    {
        return new ArrayList<>(foodsById.values());
    }

    public synchronized Diary insertDiary(Diary diary)
    {
        Long id = diary.getId();
        if (id == null)
        {
            id = nextDiaryId++;
            diary.setId(id);
        }
        diariesById.put(id, diary);
        nextDiaryId = Math.max(nextDiaryId, id + 1);
        return diary;
    }

    public Diary findDiaryById(Long id)
    {
        return diariesById.get(id);
    }

    public synchronized void updateDiary(Diary diary)
    {
        diariesById.put(diary.getId(), diary);
    }

    public synchronized void deleteDiary(Long diaryId)
    {
        diariesById.remove(diaryId);
        List<Long> destIds = diaryDestinationIdsByDiaryId.remove(diaryId);
        if (destIds != null)
        {
            for (Long destId : destIds)
            {
                List<Long> diaryIds = diaryIdsByDestinationId.get(destId);
                if (diaryIds != null)
                {
                    diaryIds.remove(diaryId);
                }
            }
        }
    }

    public synchronized void replaceDiaryDestinations(Long diaryId, List<Long> destinationIds)
    {
        // Remove old reverse index
        List<Long> old = diaryDestinationIdsByDiaryId.get(diaryId);
        if (old != null)
        {
            for (Long destId : old)
            {
                List<Long> diaryIds = diaryIdsByDestinationId.get(destId);
                if (diaryIds != null)
                {
                    diaryIds.remove(diaryId);
                }
            }
        }

        List<Long> newList = new ArrayList<>();
        for (Long destId : destinationIds)
        {
            newList.add(destId);
            diaryIdsByDestinationId.computeIfAbsent(destId, k -> new ArrayList<>()).add(diaryId);
        }

        diaryDestinationIdsByDiaryId.put(diaryId, newList);
    }

    public List<Long> getDestinationsByDiaryId(Long diaryId)
    {
        List<Long> ids = diaryDestinationIdsByDiaryId.get(diaryId);
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    public List<Long> getDiaryIdsByDestinationId(Long destinationId)
    {
        List<Long> ids = diaryIdsByDestinationId.get(destinationId);
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    public synchronized Comment insertComment(Comment comment)
    {
        Long id = comment.getId();
        if (id == null)
        {
            id = nextCommentId++;
            comment.setId(id);
        }
        commentsById.put(id, comment);
        nextCommentId = Math.max(nextCommentId, id + 1);

        String targetKey = comment.getTargetType() + "#" + comment.getTargetId();
        RatingAgg agg = ratingAggByTargetKey.get(targetKey);
        if (agg == null)
        {
            agg = new RatingAgg();
            ratingAggByTargetKey.put(targetKey, agg);
        }
        agg.sum += comment.getRating() == null ? 0.0 : comment.getRating();
        agg.count += 1;
        return comment;
    }

    public double getAverageRating(String targetType, Long targetId, Double fallback)
    {
        if (targetType == null || targetId == null)
        {
            return fallback == null ? 0.0 : fallback;
        }
        String targetKey = targetType + "#" + targetId;
        RatingAgg agg = ratingAggByTargetKey.get(targetKey);
        if (agg == null || agg.count == 0)
        {
            return fallback == null ? 0.0 : fallback;
        }
        return agg.sum / agg.count;
    }

    public List<Diary> findAllDiaries()
    {
        return new ArrayList<>(diariesById.values());
    }

    /**
     * 重建 Facility/Food/Diary 的 Trie/倒排索引（模糊/全文检索）。
     */
    public synchronized void rebuildSearchIndicesAll()
    {
        // 由于 Trie/倒排实现里没有 clear()，用新对象重建最简单可靠。
        // （该方法只在启动阶段调用一次，成本可接受）
        rebuildFacilityFoodDiarySearchIndices();
    }

    // ------------------- Search APIs (Facility/Food/Diary) -------------------

    public List<Facility> searchFacilities(String keyword, String type, Long areaId, int limit)
    {
        int l = limit <= 0 ? 50 : limit;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (!hasKeyword)
        {
            if (!isBlank(type))
            {
                return findFacilitiesByAreaIdAndType(areaId, type);
            }
            if (areaId == null)
            {
                return findAllFacilities();
            }
            List<Facility> result = new ArrayList<>();
            for (Facility f : facilitiesById.values())
            {
                if (areaId.equals(f.getAreaId()))
                {
                    result.add(f);
                }
            }
            return result;
        }

        List<Long> candidateIds = facilityNGramIndex.search(keyword, l * 5);
        List<Facility> result = new ArrayList<>(Math.min(l, candidateIds.size()));
        for (Long id : candidateIds)
        {
            Facility f = facilitiesById.get(id);
            if (f == null)
            {
                continue;
            }
            if (areaId != null && !areaId.equals(f.getAreaId()))
            {
                continue;
            }
            if (!isBlank(type) && !type.equals(f.getType()))
            {
                continue;
            }
            result.add(f);
            if (result.size() >= l)
            {
                break;
            }
        }
        return result;
    }

    public List<Food> searchFoods(String keyword, String cuisine, Long areaId, int limit)
    {
        int l = limit <= 0 ? 50 : limit;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (!hasKeyword)
        {
            if (!isBlank(cuisine))
            {
                List<Food> result = new ArrayList<>();
                for (Food f : (areaId == null ? findAllFoods() : findFoodsByAreaId(areaId)))
                {
                    if (cuisine.equals(f.getCuisine()))
                    {
                        result.add(f);
                    }
                }
                return result;
            }
            return areaId == null ? findAllFoods() : findFoodsByAreaId(areaId);
        }

        List<Long> candidateIds = foodNGramIndex.search(keyword, l * 5);
        List<Food> result = new ArrayList<>(Math.min(l, candidateIds.size()));
        for (Long id : candidateIds)
        {
            Food f = foodsById.get(id);
            if (f == null)
            {
                continue;
            }
            if (areaId != null && !areaId.equals(f.getAreaId()))
            {
                continue;
            }
            if (!isBlank(cuisine))
            {
                if (f.getCuisine() == null || !cuisine.equals(f.getCuisine()))
                {
                    continue;
                }
            }
            result.add(f);
            if (result.size() >= l)
            {
                break;
            }
        }
        return result;
    }

    public List<Diary> searchDiaries(String keyword, Long userId, Long destinationId, int limit)
    {
        int l = limit <= 0 ? 50 : limit;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (!hasKeyword)
        {
            // no keyword: just return by filter
            List<Diary> result = new ArrayList<>();
            for (Diary d : diariesById.values())
            {
                if (userId != null && !userId.equals(d.getUserId()))
                {
                    continue;
                }
                if (destinationId != null)
                {
                    List<Long> destIds = diaryDestinationIdsByDiaryId.get(d.getId());
                    if (destIds == null || !destIds.contains(destinationId))
                    {
                        continue;
                    }
                }
                result.add(d);
                if (result.size() >= l)
                {
                    break;
                }
            }
            return result;
        }

        List<Long> candidateIds = diaryFullTextIndex.search(keyword, l * 10);
        List<Diary> result = new ArrayList<>(Math.min(l, candidateIds.size()));
        for (Long id : candidateIds)
        {
            Diary d = diariesById.get(id);
            if (d == null)
            {
                continue;
            }
            if (userId != null && !userId.equals(d.getUserId()))
            {
                continue;
            }
            if (destinationId != null)
            {
                List<Long> destIds = diaryDestinationIdsByDiaryId.get(d.getId());
                if (destIds == null || !destIds.contains(destinationId))
                {
                    continue;
                }
            }
            result.add(d);
            if (result.size() >= l)
            {
                break;
            }
        }
        return result;
    }

    private boolean isBlank(String s)
    {
        return s == null || s.trim().isEmpty();
    }

    private void rebuildFacilityFoodDiarySearchIndices()
    {
        // 直接换新索引对象，避免 Trie/倒排实现缺少 clear()。
        facilityPrefixTrieIndex = new PrefixTrieIdIndex();
        facilityNGramIndex = new NGramInvertedIndex();

        for (Facility f : facilitiesById.values())
        {
            if (f.getName() != null)
            {
                facilityPrefixTrieIndex.insert(f.getName(), f.getId());
                facilityNGramIndex.insert(f.getName(), f.getId());
            }
            if (f.getType() != null)
            {
                facilityPrefixTrieIndex.insert(f.getType(), f.getId());
                facilityNGramIndex.insert(f.getType(), f.getId());
            }
        }

        foodPrefixTrieIndex = new PrefixTrieIdIndex();
        foodNGramIndex = new NGramInvertedIndex();

        for (Food f : foodsById.values())
        {
            if (f.getName() != null)
            {
                foodPrefixTrieIndex.insert(f.getName(), f.getId());
                foodNGramIndex.insert(f.getName(), f.getId());
            }
            if (f.getDescription() != null)
            {
                foodNGramIndex.insert(f.getDescription(), f.getId());
            }
            if (f.getCuisine() != null)
            {
                foodPrefixTrieIndex.insert(f.getCuisine(), f.getId());
                foodNGramIndex.insert(f.getCuisine(), f.getId());
            }
        }

        diaryFullTextIndex = new NGramInvertedIndex();

        for (Diary d : diariesById.values())
        {
            if (d.getTitle() != null)
            {
                diaryFullTextIndex.insert(d.getTitle(), d.getId());
            }
            if (d.getContent() != null)
            {
                diaryFullTextIndex.insert(d.getContent(), d.getId());
            }
        }
    }

    public static final class RatingAgg
    {
        public double sum;
        public long count;
    }
}

