package com.travel.service;

import com.travel.model.dto.diary.DiaryCreateRequest;
import com.travel.model.dto.diary.DiaryUpdateRequest;
import com.travel.model.vo.diary.DiaryDetailVO;
import com.travel.model.entity.Diary;

import java.util.List;

/**
 * 日记服务。
 */
public interface DiaryService
{

    Long create(Long userId, DiaryCreateRequest request);

    void update(Long userId, DiaryUpdateRequest request);

    void delete(Long userId, Long diaryId);

    DiaryDetailVO detail(Long diaryId);

    List<Diary> list(Integer page, Integer size, String sortBy);

    List<Diary> search(String keyword, Long destinationId, Integer page, Integer size);

    void rate(Long userId, Long diaryId, double rating, String comment);
}

