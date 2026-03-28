package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.model.dto.behavior.EngagementRequest;
import com.travel.security.SecurityUtil;
import com.travel.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户行为采集接口（点赞/收藏/浏览）。
 */
@RestController
@RequestMapping("/api/behavior")
public class BehaviorController
{

    private final UserService userService;

    public BehaviorController(UserService userService)
    {
        this.userService = userService;
    }

    @PostMapping("/engage")
    public ApiResponse<Void> engage(@Valid @RequestBody EngagementRequest request)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }

        userService.recordEngagement(userId, request.getTargetType(), request.getTargetId(), request.getActionType());
        return ApiResponse.successMessage("行为记录成功");
    }
}
