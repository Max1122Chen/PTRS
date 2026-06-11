package com.travel.service.support;

import com.travel.model.entity.Comment;
import com.travel.model.entity.User;
import com.travel.model.vo.comment.CommentVO;
import com.travel.storage.InMemoryStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将内存中的评论实体组装为详情页展示对象。
 */
@Component
public class CommentAssembler
{

    private final InMemoryStore store;

    public CommentAssembler(InMemoryStore store)
    {
        this.store = store;
    }

    public List<CommentVO> listForTarget(String targetType, Long targetId)
    {
        List<Comment> comments = store.listCommentsByTarget(targetType, targetId);
        List<CommentVO> result = new ArrayList<>(comments.size());
        for (Comment comment : comments)
        {
            result.add(toVo(comment));
        }
        return result;
    }

    private CommentVO toVo(Comment comment)
    {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setRating(comment.getRating());
        vo.setCreateTime(comment.getCreateTime());

        User user = comment.getUserId() == null ? null : store.findUserById(comment.getUserId());
        if (user != null)
        {
            String nickname = user.getNickname();
            vo.setUserNickname(nickname == null || nickname.isBlank() ? user.getUsername() : nickname);
        }
        else
        {
            vo.setUserNickname("游客");
        }
        return vo;
    }
}
