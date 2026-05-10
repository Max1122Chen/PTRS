package com.travel.animation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从火山视觉 CVSync2AsyncGetResult 响应中抽取可下载视频地址（兼容多种嵌套字段与无后缀 CDN 链接）。
 */
public final class JimengVideoUrlExtractor
{

    private static final Pattern STRICT_VIDEO_EXT =
        Pattern.compile("https?://[^\\s\"'<>]+\\.(?:mp4|webm|mov)(?:\\?[^\\s\"'<>]*)?", Pattern.CASE_INSENSITIVE);

    /** 常见对象存储 / 火山 CDN：路径未必带后缀 */
    private static final Pattern LOOSE_HTTP =
        Pattern.compile("https?://[\\w\\-./%?:@&=+#~!$*(),;\\[\\]]+", Pattern.CASE_INSENSITIVE);

    private JimengVideoUrlExtractor()
    {
    }

    /**
     * 从轮询返回体（SDK 已反序列化为 Map/JSONObject/POJO）中抽取首个可用的视频 URL。
     */
    public static String extractFirstVideoUrl(Object responseObj)
    {
        if (responseObj == null)
        {
            return null;
        }
        String raw = JSON.toJSONString(responseObj);

        String u = findByStrictRegex(raw);
        if (u != null)
        {
            return u;
        }

        try
        {
            Object root = JSON.parse(raw);
            u = findInJsonTree(root, 0);
            if (u != null)
            {
                return u;
            }

            JSONObject o = root instanceof JSONObject jo ? jo : JSON.parseObject(raw);
            JSONObject data = o.getJSONObject("data");
            if (data != null)
            {
                u = unwrapNestedData(data);
                if (u != null)
                {
                    return u;
                }
            }
        }
        catch (RuntimeException ignored)
        {
        }

        return lastResortScanHttps(raw);
    }

    private static String unwrapNestedData(JSONObject data)
    {
        String rj = data.getString("resp_json");
        if (rj != null && !rj.isBlank())
        {
            try
            {
                Object nested = JSON.parse(rj);
                String u = findInJsonTree(nested, 0);
                if (u != null)
                {
                    return u;
                }
            }
            catch (RuntimeException ignored)
            {
            }
        }
        Object nestedObj = data.get("nested");
        if (nestedObj instanceof String ns && ns.length() > 10)
        {
            try
            {
                Object p = JSON.parse(ns);
                String u = findInJsonTree(p, 0);
                if (u != null)
                {
                    return u;
                }
            }
            catch (RuntimeException ignored)
            {
            }
        }
        return null;
    }

    private static String findByStrictRegex(String raw)
    {
        Matcher m = STRICT_VIDEO_EXT.matcher(raw);
        if (m.find())
        {
            return m.group().trim();
        }
        return null;
    }

    private static String lastResortScanHttps(String raw)
    {
        Matcher m = LOOSE_HTTP.matcher(raw);
        while (m.find())
        {
            String candidate = m.group().trim();
            if (looksLikeVideoDownloadUrl(candidate))
            {
                return stripTrailingGarbage(candidate);
            }
        }
        return null;
    }

    /**
     * 去掉 JSON 紧随其后的逗号、引号等误匹配尾部。
     */
    private static String stripTrailingGarbage(String u)
    {
        String t = u;
        while (!t.isEmpty() && ".,;)'\"".indexOf(t.charAt(t.length() - 1)) >= 0)
        {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    static boolean looksLikeVideoDownloadUrl(String candidate)
    {
        if (candidate == null || candidate.length() < 12)
        {
            return false;
        }
        String t = candidate.trim();
        if (!t.startsWith("http://") && !t.startsWith("https://"))
        {
            return false;
        }
        String low = t.toLowerCase();
        if (low.contains(".mp4") || low.contains(".webm") || low.contains(".mov"))
        {
            return true;
        }
        if (low.contains("/video/") || low.contains("video?") || low.contains("type=video"))
        {
            return true;
        }
        return low.contains("volces.com")
            || low.contains("volcengine")
            || low.contains("byteimg")
            || low.contains("bytecdn")
            || low.contains("pstatp")
            || low.contains("tos-")
            || low.contains("lighthouse");
    }

    private static String findInJsonTree(Object node, int depth)
    {
        if (depth > 18 || node == null)
        {
            return null;
        }
        if (node instanceof JSONObject o)
        {
            String[] keys = {
                "video_url", "url", "output_url", "download_url", "result_url", "cdn_url",
                "videoUrl", "VideoURL", "file_url", "video_path"
            };
            for (String key : keys)
            {
                if (o.containsKey(key))
                {
                    String u = asHttpUrlString(o.get(key));
                    if (u != null)
                    {
                        return u;
                    }
                }
            }
            Object vr = o.get("video_result");
            if (vr != null)
            {
                String u = findUrlInVideoResult(vr, depth + 1);
                if (u != null)
                {
                    return u;
                }
            }
            for (String key : new String[] {"result", "results", "outputs", "list", "data"})
            {
                if (o.containsKey(key))
                {
                    String u = findInJsonTree(o.get(key), depth + 1);
                    if (u != null)
                    {
                        return u;
                    }
                }
            }
            List<String> keys2 = new ArrayList<>(o.keySet());
            for (String key : keys2)
            {
                String u = findInJsonTree(o.get(key), depth + 1);
                if (u != null)
                {
                    return u;
                }
            }
        }
        else if (node instanceof JSONArray arr)
        {
            for (int i = 0; i < arr.size(); i++)
            {
                String u = findInJsonTree(arr.get(i), depth + 1);
                if (u != null)
                {
                    return u;
                }
            }
        }
        else if (node instanceof String s)
        {
            Matcher m = STRICT_VIDEO_EXT.matcher(s);
            if (m.find())
            {
                return m.group().trim();
            }
            if (looksLikeVideoDownloadUrl(s))
            {
                return stripTrailingGarbage(s.trim());
            }
        }
        return null;
    }

    private static String findUrlInVideoResult(Object vr, int depth)
    {
        if (vr instanceof JSONArray arr)
        {
            for (int i = 0; i < arr.size(); i++)
            {
                Object el = arr.get(i);
                if (el instanceof JSONObject jo)
                {
                    if (jo.containsKey("url"))
                    {
                        String u = asHttpUrlString(jo.get("url"));
                        if (u != null)
                        {
                            return u;
                        }
                    }
                    String u = findInJsonTree(el, depth);
                    if (u != null)
                    {
                        return u;
                    }
                }
            }
        }
        else if (vr instanceof JSONObject jo)
        {
            return findInJsonTree(jo, depth);
        }
        return null;
    }

    private static String asHttpUrlString(Object v)
    {
        if (v == null)
        {
            return null;
        }
        if (v instanceof String s)
        {
            s = s.trim();
            if (s.isEmpty())
            {
                return null;
            }
            Matcher m = STRICT_VIDEO_EXT.matcher(s);
            if (m.find())
            {
                return m.group().trim();
            }
            if (looksLikeVideoDownloadUrl(s))
            {
                return stripTrailingGarbage(s);
            }
            return null;
        }
        return null;
    }
}
