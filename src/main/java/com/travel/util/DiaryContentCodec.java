package com.travel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 日记正文存储编解码器：支持 RAW 与 GZIP+Base64 两种格式。
 */
@Component
public class DiaryContentCodec
{

    private static final Logger log = LoggerFactory.getLogger(DiaryContentCodec.class);

    private static final String RAW_PREFIX = "RAW:";

    private static final String GZIP_BASE64_PREFIX = "GZB64:";

    private final boolean compressionEnabled;

    private final int thresholdBytes;

    public DiaryContentCodec(
        @Value("${app.diary.content-compression.enabled:true}") boolean compressionEnabled,
        @Value("${app.diary.content-compression.threshold-bytes:256}") int thresholdBytes)
    {
        this.compressionEnabled = compressionEnabled;
        this.thresholdBytes = Math.max(0, thresholdBytes);
    }

    public String encodeForStorage(String plainContent)
    {
        if (plainContent == null)
        {
            return null;
        }

        if (!compressionEnabled)
        {
            return plainContent;
        }

        byte[] rawBytes = plainContent.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length < thresholdBytes)
        {
            return RAW_PREFIX + plainContent;
        }

        try
        {
            byte[] compressed = gzip(rawBytes);
            if (compressed.length >= rawBytes.length)
            {
                return RAW_PREFIX + plainContent;
            }
            return GZIP_BASE64_PREFIX + Base64.getEncoder().encodeToString(compressed);
        }
        catch (Exception ex)
        {
            log.warn("Diary content compression failed, fallback to RAW. cause={}: {}",
                ex.getClass().getName(), ex.getMessage());
            return RAW_PREFIX + plainContent;
        }
    }

    public String decodeFromStorage(String storedContent)
    {
        if (storedContent == null)
        {
            return null;
        }

        if (storedContent.startsWith(RAW_PREFIX))
        {
            return storedContent.substring(RAW_PREFIX.length());
        }

        if (!storedContent.startsWith(GZIP_BASE64_PREFIX))
        {
            // 兼容历史数据（无前缀，明文存储）。
            return storedContent;
        }

        try
        {
            String payload = storedContent.substring(GZIP_BASE64_PREFIX.length());
            byte[] compressed = Base64.getDecoder().decode(payload);
            byte[] rawBytes = gunzip(compressed);
            return new String(rawBytes, StandardCharsets.UTF_8);
        }
        catch (Exception ex)
        {
            log.warn("Diary content decompression failed, keep original payload. cause={}: {}",
                ex.getClass().getName(), ex.getMessage());
            return storedContent;
        }
    }

    private byte[] gzip(byte[] source) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos))
        {
            gzip.write(source);
        }
        return baos.toByteArray();
    }

    private byte[] gunzip(byte[] compressed) throws IOException
    {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzipInputStream.read(buffer)) >= 0)
            {
                output.write(buffer, 0, n);
            }
            return output.toByteArray();
        }
    }
}