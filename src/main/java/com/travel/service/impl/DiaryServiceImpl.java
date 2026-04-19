package com.travel.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.storage.InMemoryStore;
import com.travel.mapper.CommentMapper;
import com.travel.mapper.DiaryDestinationMapper;
import com.travel.mapper.DiaryMapper;
import com.travel.model.dto.diary.DiaryCreateRequest;
import com.travel.model.dto.diary.DiaryUpdateRequest;
import com.travel.model.entity.Diary;
import com.travel.model.entity.DiaryDestination;
import com.travel.model.entity.User;
import com.travel.model.vo.diary.DiaryDetailVO;
import com.travel.service.DiaryService;
import com.travel.util.DiaryContentCodec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 日记服务实现。
 */
@Service
public class DiaryServiceImpl implements DiaryService
{

    private static final Logger log = LoggerFactory.getLogger(DiaryServiceImpl.class);

    /**
     * 日记热度浏览权重：固定为 1.0（即每次浏览 +1）。
     */
    private static final int DIARY_VIEW_HEAT_WEIGHT = 1;

    @Value("${app.debug.ignore-db-connection-failure:false}")
    private boolean ignoreDbConnectionFailure;

    private final InMemoryStore store;

    private final ObjectMapper objectMapper;

    private final DiaryMapper diaryMapper;

    private final DiaryDestinationMapper diaryDestinationMapper;

    private final CommentMapper commentMapper;

    private final DiaryContentCodec diaryContentCodec;

    public DiaryServiceImpl(
        InMemoryStore store,
        ObjectMapper objectMapper,
        DiaryMapper diaryMapper,
        DiaryDestinationMapper diaryDestinationMapper,
        CommentMapper commentMapper,
        DiaryContentCodec diaryContentCodec)
    {
        this.store = store;
        this.objectMapper = objectMapper;
        this.diaryMapper = diaryMapper;
        this.diaryDestinationMapper = diaryDestinationMapper;
        this.commentMapper = commentMapper;
        this.diaryContentCodec = diaryContentCodec;
    }

    @Override
    public Long create(Long userId, DiaryCreateRequest request)
    {
        LocalDateTime now = LocalDateTime.now();
        List<Long> destinations = request.getDestinations() == null ? List.of() : request.getDestinations();

        Diary diary = new Diary();
        diary.setUserId(userId);
        diary.setTitle(request.getTitle());
        diary.setContent(request.getContent());
        diary.setImages(toJson(request.getImages()));
        diary.setVideos(toJson(request.getVideos()));
        diary.setHeat(0);
        diary.setRating(0.0);
        diary.setCreateTime(now);
        diary.setUpdateTime(now);

        // 优先落库；若开发模式下数据库不可用，则回退到内存写入。
        runDbWrite("create-diary", () ->
        {
            Diary dbDiary = toDiaryForDb(diary);
            diaryMapper.insert(dbDiary);
            if (dbDiary.getId() == null)
            {
                throw new IllegalStateException("diary 插入后 id 为空");
            }
            diary.setId(dbDiary.getId());

            for (Long destId : destinations)
            {
                DiaryDestination dd = new DiaryDestination();
                dd.setDiaryId(diary.getId());
                dd.setDestinationId(destId);
                dd.setCreateTime(now);
                diaryDestinationMapper.insert(dd);
            }
        });

        // 再更新内存索引
        store.insertDiary(diary);
        store.replaceDiaryDestinations(diary.getId(), destinations);
        store.rebuildSearchIndicesAll();
        return diary.getId();
    }

    @Override
    public void update(Long userId, DiaryUpdateRequest request)
    {
        Diary existing = store.findDiaryById(request.getId());
        if (existing == null)
        {
            throw new IllegalArgumentException("日记不存在");
        }
        if (!Objects.equals(existing.getUserId(), userId))
        {
            throw new IllegalArgumentException("无权限操作该日记");
        }

        Diary update = new Diary();
        update.setId(existing.getId());
        update.setUserId(existing.getUserId());
        update.setTitle(request.getTitle());
        update.setContent(request.getContent());
        update.setImages(toJson(request.getImages()));
        update.setVideos(toJson(request.getVideos()));
        update.setUpdateTime(LocalDateTime.now());
        update.setCreateTime(existing.getCreateTime());
        update.setHeat(existing.getHeat());
        update.setRating(existing.getRating());

        // 先尝试落库；若开发模式下数据库不可用，则回退到内存更新。
        runDbWrite("update-diary", () ->
        {
            diaryMapper.updateById(toDiaryForDb(update));

            // destinations 可选；不传则保持不变
            if (request.getDestinations() != null)
            {
                Long diaryId = existing.getId();
                diaryDestinationMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DiaryDestination>()
                        .eq(DiaryDestination::getDiaryId, diaryId));

                LocalDateTime now = LocalDateTime.now();
                for (Long destId : request.getDestinations())
                {
                    DiaryDestination dd = new DiaryDestination();
                    dd.setDiaryId(diaryId);
                    dd.setDestinationId(destId);
                    dd.setCreateTime(now);
                    diaryDestinationMapper.insert(dd);
                }
            }
        });

        // 内存更新
        store.updateDiary(update);
        if (request.getDestinations() != null)
        {
            store.replaceDiaryDestinations(existing.getId(), request.getDestinations());
        }
        store.rebuildSearchIndicesAll();
    }

    @Override
    public void delete(Long userId, Long diaryId)
    {
        Diary existing = store.findDiaryById(diaryId);
        if (existing == null)
        {
            throw new IllegalArgumentException("日记不存在");
        }
        if (!Objects.equals(existing.getUserId(), userId))
        {
            throw new IllegalArgumentException("无权限操作该日记");
        }

        runDbWrite("delete-diary", () ->
        {
            diaryDestinationMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DiaryDestination>()
                    .eq(DiaryDestination::getDiaryId, diaryId));
            diaryMapper.deleteById(diaryId);
        });

        // 内存
        store.deleteDiary(diaryId);
        store.rebuildSearchIndicesAll();
    }

    @Override
    public DiaryDetailVO detail(Long diaryId)
    {
        Diary diary = store.findDiaryById(diaryId);
        if (diary == null)
        {
            throw new IllegalArgumentException("日记不存在");
        }

        // 浏览热度：固定权重模型（权重=1.0），每次详情访问 heat + 1。
        Diary update = new Diary();
        update.setId(diaryId);
        update.setHeat(nextHeatByView(diary.getHeat()));
        update.setCreateTime(diary.getCreateTime());
        update.setRating(diary.getRating());
        update.setTitle(diary.getTitle());
        update.setContent(diary.getContent());
        update.setImages(diary.getImages());
        update.setVideos(diary.getVideos());
        update.setUserId(diary.getUserId());
        update.setUpdateTime(LocalDateTime.now());
        store.updateDiary(update);
        diary.setHeat(update.getHeat());
        List<Long> destIds = store.getDestinationsByDiaryId(diaryId);

        DiaryDetailVO vo = new DiaryDetailVO();
        vo.setDiary(diary);
        vo.setDestinations(destIds);

        User creator = store.findUserById(diary.getUserId());
        vo.setCreatorNickname(creator == null ? null : creator.getNickname());
        return vo;
    }

    @Override
    public List<Diary> list(Integer page, Integer size, String sortBy)
    {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size <= 0 ? 10 : Math.min(size, 50);
        int offset = (p - 1) * s;

        List<Diary> all = store.findAllDiaries();
        all.sort((a, b) ->
        {
            if ("rating".equalsIgnoreCase(sortBy))
            {
                double ra = a.getRating() == null ? 0.0 : a.getRating();
                double rb = b.getRating() == null ? 0.0 : b.getRating();
                return Double.compare(rb, ra);
            }
            int ha = a.getHeat() == null ? 0 : a.getHeat();
            int hb = b.getHeat() == null ? 0 : b.getHeat();
            if (ha != hb)
            {
                return Integer.compare(hb, ha);
            }
            // createTime 降序，null 视为最小
            LocalDateTime ta = a.getCreateTime();
            LocalDateTime tb = b.getCreateTime();
            if (ta == null && tb == null)
            {
                return 0;
            }
            if (ta == null)
            {
                return 1;
            }
            if (tb == null)
            {
                return -1;
            }
            return tb.compareTo(ta);
        });

        if (offset >= all.size())
        {
            return List.of();
        }
        int to = Math.min(offset + s, all.size());
        return all.subList(offset, to);
    }

    @Override
    public List<Diary> search(String keyword, Long destinationId, Integer page, Integer size)
    {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size <= 0 ? 10 : Math.min(size, 50);
        int offset = (p - 1) * s;

        // 内存：先用索引拿到候选，再按 heat/rating 排序后分页。
        int fetch = offset + s * 10;
        if (fetch < offset + s)
        {
            fetch = offset + s;
        }

        List<Diary> candidates = store.searchDiaries(keyword, null, destinationId, fetch);
        if (candidates.isEmpty())
        {
            return List.of();
        }

        candidates.sort((a, b) ->
        {
            int ha = a.getHeat() == null ? 0 : a.getHeat();
            int hb = b.getHeat() == null ? 0 : b.getHeat();
            if (ha != hb)
            {
                return Integer.compare(hb, ha);
            }
            double ra = a.getRating() == null ? 0.0 : a.getRating();
            double rb = b.getRating() == null ? 0.0 : b.getRating();
            return Double.compare(rb, ra);
        });

        if (offset >= candidates.size())
        {
            return List.of();
        }
        int to = Math.min(offset + s, candidates.size());
        return candidates.subList(offset, to);
    }

    @Override
    public void rate(Long userId, Long diaryId, double rating)
    {
        Diary diary = store.findDiaryById(diaryId);
        if (diary == null)
        {
            throw new IllegalArgumentException("日记不存在");
        }

        com.travel.model.entity.Comment c = new com.travel.model.entity.Comment();
        c.setUserId(userId);
        c.setTargetId(diaryId);
        c.setTargetType("DIARY");
        c.setContent("");
        c.setRating(rating);
        LocalDateTime now = LocalDateTime.now();
        c.setCreateTime(now);
        c.setUpdateTime(now);

        runDbWrite("insert-diary-comment", () -> commentMapper.insert(c));

        store.insertComment(c);

        double avg = store.getAverageRating("DIARY", diaryId, rating);
        diary.setRating(avg);
        diary.setUpdateTime(LocalDateTime.now());

        runDbWrite("update-diary-rating", () -> diaryMapper.updateById(toDiaryForDb(diary)));

        // 内存更新
        store.updateDiary(diary);
    }

    private Diary toDiaryForDb(Diary source)
    {
        Diary target = new Diary();
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setTitle(source.getTitle());
        target.setContent(diaryContentCodec.encodeForStorage(source.getContent()));
        target.setImages(source.getImages());
        target.setVideos(source.getVideos());
        target.setHeat(source.getHeat());
        target.setRating(source.getRating());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private void runDbWrite(String operation, Runnable action)
    {
        try
        {
            action.run();
        }
        catch (RuntimeException ex)
        {
            if (ignoreDbConnectionFailure && isDbUnavailable(ex))
            {
                log.warn("DB write skipped for {} because DB is unavailable and app.debug.ignore-db-connection-failure=true. " +
                    "Falling back to in-memory write. cause={}: {}", operation, ex.getClass().getName(), ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private boolean isDbUnavailable(Throwable ex)
    {
        Throwable current = ex;
        while (current != null)
        {
            String name = current.getClass().getName();
            if (name.contains("DataSourceDisableException")
                || name.contains("CannotCreateTransactionException")
                || name.contains("CannotGetJdbcConnectionException")
                || name.contains("DataAccessResourceFailureException")
                || name.contains("CommunicationsException"))
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private int nextHeatByView(Integer currentHeat)
    {
        int base = currentHeat == null ? 0 : currentHeat;
        return base + DIARY_VIEW_HEAT_WEIGHT;
    }

    private String toJson(List<String> list)
    {
        if (list == null)
        {
            return null;
        }
        try
        {
            return objectMapper.writeValueAsString(list);
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("图片/视频字段格式错误");
        }
    }

    @SuppressWarnings("unused")
    private List<String> fromJson(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return List.of();
        }
        try
        {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        }
        catch (Exception ex)
        {
            return List.of();
        }
    }
}

