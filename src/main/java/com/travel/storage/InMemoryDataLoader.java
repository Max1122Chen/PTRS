package com.travel.storage;

import com.travel.mapper.PoiMapper;
import com.travel.mapper.CommentMapper;
import com.travel.mapper.DiaryDestinationMapper;
import com.travel.mapper.DiaryMapper;
import com.travel.mapper.FacilityMapper;
import com.travel.mapper.FoodMapper;
import com.travel.mapper.RoadMapper;
import com.travel.mapper.RestaurantMapper;
import com.travel.mapper.ScenicAreaMapper;
import com.travel.mapper.ScenicAreaTagMapper;
import com.travel.mapper.TagMapper;
import com.travel.mapper.UserInterestMapper;
import com.travel.mapper.UserMapper;
import com.travel.model.entity.Comment;
import com.travel.model.entity.Diary;
import com.travel.model.entity.DiaryDestination;
import com.travel.model.entity.Facility;
import com.travel.model.entity.Food;
import com.travel.model.entity.Poi;
import com.travel.model.entity.Road;
import com.travel.model.entity.Restaurant;
import com.travel.model.entity.ScenicArea;
import com.travel.model.entity.ScenicAreaTag;
import com.travel.model.entity.Tag;
import com.travel.model.entity.User;
import com.travel.model.entity.UserInterest;
import com.travel.util.DiaryContentCodec;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时数据预加载器。
 *
 * <p>
 * 仅在启动阶段读取数据库并写入内存。
 * 运行期业务服务应只依赖 {@link InMemoryStore}。
 * </p>
 */
@Component
public class InMemoryDataLoader
{

    private static final Logger log = LoggerFactory.getLogger(InMemoryDataLoader.class);

    private final InMemoryStore store;

    private final UserMapper userMapper;

    private final UserInterestMapper userInterestMapper;

    private final ScenicAreaMapper scenicAreaMapper;

    private final ScenicAreaTagMapper scenicAreaTagMapper;

    private final TagMapper tagMapper;

    private final PoiMapper poiMapper;

    private final RoadMapper roadMapper;

    private final FacilityMapper facilityMapper;

    private final RestaurantMapper restaurantMapper;

    private final FoodMapper foodMapper;

    private final DiaryMapper diaryMapper;

    private final DiaryDestinationMapper diaryDestinationMapper;

    private final CommentMapper commentMapper;

    private final DevSeedDataLoader devSeedDataLoader;

    private final DiaryContentCodec diaryContentCodec;

    @Value("${app.storage.preload.enabled:true}")
    private boolean preloadEnabled;

    @Value("${app.debug.ignore-db-connection-failure:false}")
    private boolean ignoreDbConnectionFailure;

    public InMemoryDataLoader(InMemoryStore store,
                               UserMapper userMapper,
                               UserInterestMapper userInterestMapper,
                               ScenicAreaMapper scenicAreaMapper,
                               ScenicAreaTagMapper scenicAreaTagMapper,
                               TagMapper tagMapper,
                               PoiMapper poiMapper,
                               RoadMapper roadMapper,
                               FacilityMapper facilityMapper,
                               RestaurantMapper restaurantMapper,
                               FoodMapper foodMapper,
                               DiaryMapper diaryMapper,
                               DiaryDestinationMapper diaryDestinationMapper,
                               CommentMapper commentMapper,
                               DevSeedDataLoader devSeedDataLoader,
                               DiaryContentCodec diaryContentCodec)
    {
        this.store = store;
        this.userMapper = userMapper;
        this.userInterestMapper = userInterestMapper;
        this.scenicAreaMapper = scenicAreaMapper;
        this.scenicAreaTagMapper = scenicAreaTagMapper;
        this.tagMapper = tagMapper;
        this.poiMapper = poiMapper;
        this.roadMapper = roadMapper;
        this.facilityMapper = facilityMapper;
        this.restaurantMapper = restaurantMapper;
        this.foodMapper = foodMapper;
        this.diaryMapper = diaryMapper;
        this.diaryDestinationMapper = diaryDestinationMapper;
        this.commentMapper = commentMapper;
        this.devSeedDataLoader = devSeedDataLoader;
        this.diaryContentCodec = diaryContentCodec;
    }

    @PostConstruct
    public void load()
    {
        if (!preloadEnabled)
        {
            log.info("In-memory preload is disabled by config: app.storage.preload.enabled=false");
            devSeedDataLoader.loadSeedIfEnabled("preload-disabled");
            return;
        }

        try
        {
        // 1) Users
        List<User> users = userMapper.selectList(null);
        for (User u : users)
        {
            store.insertUser(u);
        }

        // 2) User interests
        List<UserInterest> allInterests = userInterestMapper.selectList(null);
        Map<Long, List<UserInterest>> interestsByUserId = new HashMap<>();
        for (UserInterest ui : allInterests)
        {
            interestsByUserId.computeIfAbsent(ui.getUserId(), k -> new ArrayList<>()).add(ui);
        }
        for (Map.Entry<Long, List<UserInterest>> e : interestsByUserId.entrySet())
        {
            store.replaceUserInterests(e.getKey(), e.getValue());
        }

        // 3) ScenicAreas
        List<ScenicArea> scenicAreas = scenicAreaMapper.selectList(null);
        for (ScenicArea sa : scenicAreas)
        {
            store.insertScenicArea(sa);
        }

        // 4) Tags + ScenicAreaTags
        List<Tag> tags = tagMapper.selectList(null);
        for (Tag t : tags)
        {
            store.insertTag(t);
        }
        List<ScenicAreaTag> relations = scenicAreaTagMapper.selectList(null);
        for (ScenicAreaTag rel : relations)
        {
            store.insertScenicAreaTag(rel);
        }
        store.rebuildScenicAreaTagWeights();

        // 5) Buildings
        List<Poi> pois = poiMapper.selectList(null);
        for (Poi p : pois)
        {
            store.insertPoi(p);
        }

        // 6) Roads
        List<Road> roads = roadMapper.selectList(null);
        for (Road r : roads)
        {
            store.insertRoad(r);
        }

        // 7) Facilities
        List<Facility> facilities = facilityMapper.selectList(null);
        for (Facility f : facilities)
        {
            store.insertFacility(f);
        }

        // 8) Restaurants
        List<Restaurant> restaurants = restaurantMapper.selectList(null);
        for (Restaurant r : restaurants)
        {
            store.insertRestaurant(r);
        }

        // 9) Foods
        List<Food> foods = foodMapper.selectList(null);
        for (Food f : foods)
        {
            store.insertFood(f);
        }

        // 10) Diaries
        List<Diary> diaries = diaryMapper.selectList(null);
        for (Diary d : diaries)
        {
            d.setContent(diaryContentCodec.decodeFromStorage(d.getContent()));
            store.insertDiary(d);
        }

        // 10.5) Build Facility/Food/Diary keyword/full-text indices
        store.rebuildSearchIndicesAll();

        // 11) Diary destinations (build indexes)
        List<DiaryDestination> dds = diaryDestinationMapper.selectList(null);
        Map<Long, List<Long>> destIdsByDiaryId = new HashMap<>();
        for (DiaryDestination dd : dds)
        {
            destIdsByDiaryId.computeIfAbsent(dd.getDiaryId(), k -> new ArrayList<>()).add(dd.getDestinationId());
        }
        for (Map.Entry<Long, List<Long>> e : destIdsByDiaryId.entrySet())
        {
            store.replaceDiaryDestinations(e.getKey(), e.getValue());
        }

        // 12) Comments (rating aggregation)
        List<Comment> comments = commentMapper.selectList(null);
        for (Comment c : comments)
        {
            store.insertComment(c);
        }
        }
        catch (Exception ex)
        {
            if (!ignoreDbConnectionFailure)
            {
                throw ex;
            }
            log.warn("In-memory preload skipped due to DB connectivity issue (app.debug.ignore-db-connection-failure=true). " +
                    "Some DB-dependent features may be unavailable until connection is restored.", ex);
            devSeedDataLoader.loadSeedIfEnabled("db-failure");
        }
    }
}

