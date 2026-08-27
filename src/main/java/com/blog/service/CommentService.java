package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.dto.CommentRequest;
import com.blog.dto.CommentVO;
import com.blog.entity.Article;
import com.blog.entity.Comment;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;

    public CommentService(CommentMapper commentMapper, ArticleMapper articleMapper) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
    }

    public List<CommentVO> listByArticleId(Long articleId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getArticleId, articleId)
                        .orderByDesc(Comment::getCreateTime));
        return comments.stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<CommentVO> listAll() {
        return commentMapper.selectList(
                new LambdaQueryWrapper<Comment>().orderByDesc(Comment::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public void addComment(Long articleId, CommentRequest request) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || article.getStatus() != 1) {
            throw new BusinessException("文章不存在或未发布");
        }
        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setNickname(request.getNickname());
        comment.setEmail(request.getEmail());
        comment.setContent(request.getContent());
        commentMapper.insert(comment);
    }

    public void delete(Long id) {
        commentMapper.deleteById(id);
    }

    private CommentVO toVO(Comment comment) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        return vo;
    }
}
