package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.model.dto.auth.LoginRequest;
import com.travel.model.dto.auth.RegisterRequest;
import com.travel.model.dto.auth.UpdateInterestRequest;
import com.travel.model.vo.auth.InterestItemVO;
import com.travel.model.vo.UserVO;
import com.travel.security.JwtUtil;
import com.travel.security.SecurityUtil;
import com.travel.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 认证与用户偏好相关接口。
 *
 * <p>
 * 对应文档中的：
 * <ul>
 *     <li>/api/auth/register</li>
 *     <li>/api/auth/login</li>
 *     <li>/api/auth/refresh</li>
 *     <li>/api/auth/interest</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController
{

    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;

    private static final Set<String> AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final UserService userService;

    private final JwtUtil jwtUtil;

    @Value("${app.media.base-path:data/media}")
    private String mediaBasePath;

    @Value("${app.media.url-prefix:/media}")
    private String mediaUrlPrefix;

    public AuthController(UserService userService, JwtUtil jwtUtil)
    {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request)
    {
        UserVO user = userService.register(request);
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", user.getId());
        data.put("username", user.getUsername());
        return ApiResponse.success(data, "注册成功");
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request)
    {
        String token = userService.login(request);
        UserVO user = userService.findByUsername(request.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return ApiResponse.success(data, "登录成功");
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader("Authorization") String authHeader)
    {
        String token = resolveToken(authHeader);
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null)
        {
            return ApiResponse.failure(401, "令牌无效");
        }
        Long userId = jwtUtil.getUserId(token);
        String username = claims.getSubject();
        String newToken = jwtUtil.generateToken(userId, username);

        Map<String, Object> data = new HashMap<>();
        data.put("token", newToken);
        return ApiResponse.success(data, "令牌刷新成功");
    }

    @PutMapping("/interest")
    public ApiResponse<Void> updateInterest(@Valid @RequestBody UpdateInterestRequest request)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        userService.updateInterests(userId, request);
        return ApiResponse.successMessage("兴趣更新成功");
    }

    @GetMapping("/interest")
    public ApiResponse<List<InterestItemVO>> getInterest()
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        return ApiResponse.success(userService.getInterests(userId), "获取成功");
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me()
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        UserVO user = userService.findById(userId);
        if (user == null)
        {
            return ApiResponse.failure(404, "用户不存在");
        }
        return ApiResponse.success(user, "获取成功");
    }

    @PostMapping("/avatar")
    public ApiResponse<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        if (file == null || file.isEmpty())
        {
            return ApiResponse.failure(400, "请上传头像文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES)
        {
            return ApiResponse.failure(400, "头像大小不能超过2MB");
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (!AVATAR_EXTENSIONS.contains(ext))
        {
            return ApiResponse.failure(400, "仅支持 JPG/PNG 格式头像");
        }

        String safeName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try
        {
            Path baseDir = Paths.get(mediaBasePath).toAbsolutePath().normalize();
            Path avatarDir = baseDir.resolve("avatar").normalize();
            Files.createDirectories(avatarDir);

            Path target = avatarDir.resolve(safeName).normalize();
            if (!target.startsWith(baseDir))
            {
                return ApiResponse.failure(400, "非法文件路径");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String url = normalizeUrlPrefix(mediaUrlPrefix) + "/avatar/" + safeName;
            UserVO updated = userService.updateAvatar(userId, url);
            return ApiResponse.success(updated, "头像上传成功");
        }
        catch (IOException e)
        {
            return ApiResponse.failure(500, "头像保存失败");
        }
    }

    private String extractExtension(String filename)
    {
        if (filename == null)
        {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx >= filename.length() - 1)
        {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeUrlPrefix(String prefix)
    {
        String out = (prefix == null || prefix.isBlank()) ? "/media" : prefix.trim();
        if (!out.startsWith("/"))
        {
            out = "/" + out;
        }
        while (out.endsWith("/"))
        {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private String resolveToken(String header)
    {
        if (header == null)
        {
            return null;
        }
        if (header.startsWith("Bearer "))
        {
            return header.substring(7);
        }
        return header;
    }
}

