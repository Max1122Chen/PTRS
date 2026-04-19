package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.model.dto.diary.DiaryCreateRequest;
import com.travel.model.dto.diary.DiaryRateRequest;
import com.travel.model.dto.diary.DiaryUpdateRequest;
import com.travel.model.entity.Diary;
import com.travel.model.vo.diary.DiaryDetailVO;
import com.travel.security.SecurityUtil;
import com.travel.service.DiaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
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
 * 旅游日记接口。
 */
@RestController
@RequestMapping("/api/diary")
public class DiaryController
{

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4");

    private final DiaryService diaryService;

    @Value("${app.media.base-path:data/media}")
    private String mediaBasePath;

    @Value("${app.media.url-prefix:/media}")
    private String mediaUrlPrefix;

    public DiaryController(DiaryService diaryService)
    {
        this.diaryService = diaryService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody DiaryCreateRequest request)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        Long diaryId = diaryService.create(userId, request);
        Map<String, Object> data = new HashMap<>();
        data.put("diary_id", diaryId);
        return ApiResponse.success(data, "创建成功");
    }

    @GetMapping
    public ApiResponse<List<Diary>> list(@RequestParam(value = "page", required = false) Integer page,
                                         @RequestParam(value = "size", required = false) Integer size,
                                         @RequestParam(value = "sortBy", required = false) String sortBy)
    {
        return ApiResponse.success(diaryService.list(page, size, sortBy), "获取成功");
    }

    @GetMapping("/{id}")
    public ApiResponse<DiaryDetailVO> detail(@PathVariable("id") @NotNull Long id)
    {
        return ApiResponse.success(diaryService.detail(id), "获取成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable("id") @NotNull Long id,
                                    @Valid @RequestBody DiaryUpdateRequest request)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        request.setId(id);
        diaryService.update(userId, request);
        return ApiResponse.successMessage("更新成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") @NotNull Long id)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        diaryService.delete(userId, id);
        return ApiResponse.successMessage("删除成功");
    }

    @GetMapping("/search")
    public ApiResponse<List<Diary>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                           @RequestParam(value = "destination", required = false) Long destination,
                                           @RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "size", required = false) Integer size)
    {
        return ApiResponse.success(diaryService.search(keyword, destination, page, size), "查询成功");
    }

    @PostMapping("/rate")
    public ApiResponse<Void> rate(@Valid @RequestBody DiaryRateRequest request)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        diaryService.rate(userId, request.getDiaryId(), request.getRating());
        return ApiResponse.successMessage("评分成功");
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> uploadAttachment(@RequestParam("file") MultipartFile file)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        if (file == null || file.isEmpty())
        {
            return ApiResponse.failure(400, "请上传文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES)
        {
            return ApiResponse.failure(400, "文件大小不能超过10MB");
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (ext.isEmpty())
        {
            return ApiResponse.failure(400, "不支持的文件类型，仅支持 JPG/PNG/MP4");
        }

        String mediaType;
        String folder;
        if (IMAGE_EXTENSIONS.contains(ext))
        {
            mediaType = "image";
            folder = "image";
        }
        else if (VIDEO_EXTENSIONS.contains(ext))
        {
            mediaType = "video";
            folder = "video";
        }
        else
        {
            return ApiResponse.failure(400, "不支持的文件类型，仅支持 JPG/PNG/MP4");
        }

        String safeName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try
        {
            Path baseDir = Paths.get(mediaBasePath).toAbsolutePath().normalize();
            Path typeDir = baseDir.resolve(folder).normalize();
            Files.createDirectories(typeDir);

            Path target = typeDir.resolve(safeName).normalize();
            if (!target.startsWith(baseDir))
            {
                return ApiResponse.failure(400, "非法文件路径");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String url = normalizeUrlPrefix(mediaUrlPrefix) + "/" + folder + "/" + safeName;
            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("mediaType", mediaType);
            data.put("originalName", file.getOriginalFilename());
            data.put("size", file.getSize());
            return ApiResponse.success(data, "上传成功");
        }
        catch (IOException e)
        {
            return ApiResponse.failure(500, "附件保存失败");
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
}

