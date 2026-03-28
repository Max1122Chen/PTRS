package com.travel.service;

import com.travel.model.dto.auth.LoginRequest;
import com.travel.model.dto.auth.RegisterRequest;
import com.travel.model.dto.auth.UpdateInterestRequest;
import com.travel.model.vo.auth.InterestItemVO;
import com.travel.model.vo.UserVO;

import java.util.List;

/**
 * 用户领域相关服务接口。
 */
public interface UserService
{

    /**
     * 用户注册。
     *
     * @param request 注册参数
     * @return 注册后的用户视图
     */
    UserVO register(RegisterRequest request);

    /**
     * 用户登录，返回访问令牌。
     *
     * @param request 登录参数
     * @return JWT 访问令牌
     */
    String login(LoginRequest request);

    /**
     * 更新当前登录用户的兴趣偏好。
     *
     * @param userId  当前用户 ID
     * @param request 更新请求
     */
    void updateInterests(Long userId, UpdateInterestRequest request);

    /**
     * 查询当前用户兴趣偏好（含权重）。
     */
    List<InterestItemVO> getInterests(Long userId);

    /**
     * 记录用户行为并增量更新兴趣偏好。
     */
    void recordEngagement(Long userId, String targetType, Long targetId, String actionType);

    /**
     * 根据用户名查询用户视图。
     *
     * @param username 用户名
     * @return 用户视图，找不到时返回 null
     */
    UserVO findByUsername(String username);
}

