package com.travel.animation;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 将日记中的 {@code /media/...} 相对 URL 解析为本地 {@link Path}。
 */
@Component
public class MediaPathResolver
{

    public Path resolveLocalFile(String mediaUrl, String mediaBasePath, String urlPrefix)
    {
        if (mediaUrl == null || mediaUrl.isBlank())
        {
            return null;
        }
        String u = mediaUrl.trim();
        if (u.startsWith("http://") || u.startsWith("https://"))
        {
            return null;
        }
        String prefix = normalizePrefix(urlPrefix);
        if (!u.startsWith(prefix))
        {
            return null;
        }
        String relative = u.substring(prefix.length());
        while (relative.startsWith("/"))
        {
            relative = relative.substring(1);
        }
        Path base = Paths.get(mediaBasePath).toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base))
        {
            return null;
        }
        if (!Files.isRegularFile(target))
        {
            return null;
        }
        return target;
    }

    /**
     * 供厂商侧访问的绝对 URL（LibTV 会话文案引用）。
     */
    public String toPublicAbsoluteUrl(String mediaUrl, String publicBaseUrl, String urlPrefix)
    {
        if (mediaUrl == null || mediaUrl.isBlank())
        {
            return null;
        }
        String u = mediaUrl.trim();
        if (u.startsWith("http://") || u.startsWith("https://"))
        {
            return u;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        if (!u.startsWith("/"))
        {
            u = "/" + u;
        }
        return base + u;
    }

    private String normalizePrefix(String urlPrefix)
    {
        String p = urlPrefix == null || urlPrefix.isBlank() ? "/media" : urlPrefix.trim();
        if (!p.startsWith("/"))
        {
            p = "/" + p;
        }
        while (p.endsWith("/"))
        {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
