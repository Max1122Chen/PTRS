package com.travel.service.impl;

import com.travel.storage.InMemoryStore;
import com.travel.model.dto.auth.InterestItemRequest;
import com.travel.model.dto.auth.LoginRequest;
import com.travel.model.dto.auth.RegisterRequest;
import com.travel.model.dto.auth.UpdateInterestRequest;
import com.travel.model.entity.User;
import com.travel.model.entity.UserBehavior;
import com.travel.model.entity.Food;
import com.travel.model.entity.ScenicArea;
import com.travel.model.entity.UserInterest;
import com.travel.model.vo.auth.InterestItemVO;
import com.travel.model.vo.UserVO;
import com.travel.security.JwtUtil;
import com.travel.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用户服务实现。
 */
@Service
public class UserServiceImpl implements UserService
{

    private static final Map<String, String> INTEREST_ALIASES = buildInterestAliases();

    private final InMemoryStore store;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    public UserServiceImpl(InMemoryStore store,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil)
    {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public UserVO register(RegisterRequest request)
    {
        if (store.findUserByUsername(request.getUsername()) != null ||
            store.findUserByEmail(request.getEmail()) != null)
        {
            throw new IllegalArgumentException("用户名或邮箱已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname());
        user.setRole("USER");
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);

        store.insertUser(user);
        return toUserVO(user);
    }

    @Override
    public String login(LoginRequest request)
    {
        User user = store.findUserByUsername(request.getUsername());
        if (user == null)
        {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
        {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public void updateInterests(Long userId, UpdateInterestRequest request)
    {
        List<InterestItemRequest> items = request.getInterests();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Double> normalized = new HashMap<>();
        for (InterestItemRequest item : items)
        {
            String type = canonicalizeInterestType(item.getType());
            if (StringUtils.isBlank(type))
            {
                continue;
            }
            double weight = item.getWeight() == null ? 1.0 : item.getWeight();
            normalized.merge(type, weight, Double::sum);
        }

        List<UserInterest> interests = new ArrayList<>(normalized.size());
        for (Map.Entry<String, Double> entry : normalized.entrySet())
        {
            UserInterest interest = new UserInterest();
            interest.setUserId(userId);
            interest.setInterestType(entry.getKey());
            interest.setWeight(roundTwo(Math.min(5.0, entry.getValue())));
            interest.setCreateTime(now);
            interests.add(interest);
        }
        store.replaceUserInterests(userId, interests);
    }

    @Override
    public List<InterestItemVO> getInterests(Long userId)
    {
        Map<String, Double> interests = store.getUserInterests(userId);
        if (interests == null || interests.isEmpty())
        {
            return List.of();
        }

        List<InterestItemVO> out = new ArrayList<>(interests.size());
        for (Map.Entry<String, Double> entry : interests.entrySet())
        {
            InterestItemVO item = new InterestItemVO();
            item.setType(entry.getKey());
            item.setWeight(entry.getValue());
            out.add(item);
        }
        return out;
    }

    @Override
    public void recordEngagement(Long userId, String targetType, Long targetId, String actionType)
    {
        if (userId == null || targetId == null)
        {
            throw new IllegalArgumentException("行为参数不完整");
        }

        String normalizedTarget = normalizeToken(targetType);
        String normalizedAction = normalizeToken(actionType);
        if (StringUtils.isBlank(normalizedTarget) || StringUtils.isBlank(normalizedAction))
        {
            throw new IllegalArgumentException("行为类型不合法");
        }

        Map<String, Double> deltaWeights = switch (normalizedTarget)
        {
            case "SCENIC" -> scenicDelta(targetId, normalizedAction);
            case "FOOD" -> foodDelta(targetId, normalizedAction);
            default -> throw new IllegalArgumentException("暂不支持的目标类型: " + targetType);
        };

        if (deltaWeights.isEmpty())
        {
            throw new IllegalArgumentException("目标不存在或缺少可学习标签");
        }

        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setTargetType(normalizedTarget);
        behavior.setTargetId(targetId);
        behavior.setActionType(normalizedAction);
        behavior.setWeight(actionBaseWeight(normalizedAction));
        behavior.setCreateTime(LocalDateTime.now());
        store.insertUserBehavior(behavior);

        Map<String, Double> merged = new HashMap<>();
        Map<String, Double> existing = store.getUserInterests(userId);
        if (existing != null)
        {
            for (Map.Entry<String, Double> entry : existing.entrySet())
            {
                String normalized = canonicalizeInterestType(entry.getKey());
                if (StringUtils.isBlank(normalized))
                {
                    continue;
                }
                merged.merge(normalized, entry.getValue() == null ? 0.0 : entry.getValue(), Double::sum);
            }
        }
        for (Map.Entry<String, Double> entry : deltaWeights.entrySet())
        {
            String tag = canonicalizeInterestType(entry.getKey());
            if (StringUtils.isBlank(tag))
            {
                continue;
            }
            double oldWeight = merged.getOrDefault(tag, 0.0);
            double nextWeight = Math.min(5.0, oldWeight + (entry.getValue() == null ? 0.0 : entry.getValue()));
            if (nextWeight > 0)
            {
                merged.put(tag, roundTwo(nextWeight));
            }
        }

        List<UserInterest> interests = new ArrayList<>(merged.size());
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Double> entry : merged.entrySet())
        {
            UserInterest interest = new UserInterest();
            interest.setUserId(userId);
            interest.setInterestType(entry.getKey());
            interest.setWeight(roundTwo(entry.getValue()));
            interest.setCreateTime(now);
            interests.add(interest);
        }
        store.replaceUserInterests(userId, interests);
    }

    @Override
    public UserVO findByUsername(String username)
    {
        User user = store.findUserByUsername(username);
        if (user == null)
        {
            return null;
        }
        return toUserVO(user);
    }

    private UserVO toUserVO(User user)
    {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        return vo;
    }

    private Map<String, Double> scenicDelta(Long scenicId, String actionType)
    {
        ScenicArea scenicArea = store.findScenicAreaById(scenicId);
        if (scenicArea == null)
        {
            return Map.of();
        }
        Map<String, Double> tags = store.getScenicAreaTagWeights(scenicId);
        if (tags == null || tags.isEmpty())
        {
            return Map.of();
        }
        double actionWeight = actionBaseWeight(actionType);
        Map<String, Double> delta = new HashMap<>();
        for (Map.Entry<String, Double> entry : tags.entrySet())
        {
            double tagWeight = entry.getValue() == null ? 1.0 : entry.getValue();
            String normalizedTag = canonicalizeInterestType(entry.getKey());
            if (StringUtils.isBlank(normalizedTag))
            {
                continue;
            }
            delta.put(normalizedTag, roundTwo(actionWeight * tagWeight));
        }
        return delta;
    }

    private Map<String, Double> foodDelta(Long foodId, String actionType)
    {
        Food food = store.findFoodById(foodId);
        if (food == null)
        {
            return Map.of();
        }

        double actionWeight = actionBaseWeight(actionType);
        Map<String, Double> delta = new HashMap<>();
        if (StringUtils.isNotBlank(food.getCuisine()))
        {
            String cuisineTag = canonicalizeInterestType(food.getCuisine());
            if (StringUtils.isNotBlank(cuisineTag))
            {
                delta.put(cuisineTag, roundTwo(actionWeight * 0.8));
            }
        }

        if (food.getAreaId() != null)
        {
            Map<String, Double> scenicTags = store.getScenicAreaTagWeights(food.getAreaId());
            if (scenicTags != null)
            {
                for (Map.Entry<String, Double> entry : scenicTags.entrySet())
                {
                    double tagWeight = entry.getValue() == null ? 1.0 : entry.getValue();
                    String normalizedTag = canonicalizeInterestType(entry.getKey());
                    if (StringUtils.isBlank(normalizedTag))
                    {
                        continue;
                    }
                    delta.merge(normalizedTag, roundTwo(actionWeight * 0.5 * tagWeight), Double::sum);
                }
            }
        }

        delta.merge("food", roundTwo(actionWeight), Double::sum);
        return delta;
    }

    private double actionBaseWeight(String actionType)
    {
        return switch (actionType)
        {
            case "FAVORITE" -> 0.35;
            case "LIKE" -> 0.2;
            case "VIEW" -> 0.08;
            default -> throw new IllegalArgumentException("暂不支持的行为类型: " + actionType);
        };
    }

    private String normalizeToken(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        return raw.trim().toUpperCase();
    }

    private String canonicalizeInterestType(String raw)
    {
        if (StringUtils.isBlank(raw))
        {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        String mapped = INTEREST_ALIASES.get(normalized);
        return mapped == null ? normalized : mapped;
    }

    private double roundTwo(double value)
    {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static Map<String, String> buildInterestAliases()
    {
        Map<String, String> map = new HashMap<>();
        map.put("自然", "nature");
        map.put("山岳", "nature");
        map.put("湖泊", "lake");
        map.put("湖", "lake");
        map.put("历史", "history");
        map.put("文化", "culture");
        map.put("校园", "campus");
        map.put("摄影", "photo");
        map.put("拍照", "photo");
        map.put("博物馆", "museum");
        map.put("艺术", "art");
        map.put("科学", "science");
        map.put("建筑", "architecture");
        map.put("夜景", "night");
        map.put("夜游", "night");
        map.put("徒步", "hiking");
        map.put("漫步", "walk");
        map.put("美食", "food");
        return map;
    }
}

