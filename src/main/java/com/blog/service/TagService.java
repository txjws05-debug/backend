package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.entity.ArticleTag;
import com.blog.entity.Tag;
import com.blog.mapper.ArticleTagMapper;
import com.blog.mapper.TagMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY_TAGS = "blog:tags";
    private static final long CACHE_TTL_HOURS = 1;

    public TagService(TagMapper tagMapper, ArticleTagMapper articleTagMapper,
                      RedisTemplate<String, Object> redisTemplate) {
        this.tagMapper = tagMapper;
        this.articleTagMapper = articleTagMapper;
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public List<Tag> listAll() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY_TAGS);
        if (cached instanceof List) {
            return (List<Tag>) cached;
        }
        List<Tag> list = tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getId));
        redisTemplate.opsForValue().set(CACHE_KEY_TAGS, list, CACHE_TTL_HOURS, TimeUnit.HOURS);
        return list;
    }

    public void create(Tag tag) {
        Long count = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>().eq(Tag::getName, tag.getName()));
        if (count > 0) {
            throw new BusinessException("标签名称已存在");
        }
        tagMapper.insert(tag);
        redisTemplate.delete(CACHE_KEY_TAGS);
    }

    public void update(Long id, Tag tag) {
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("标签不存在");
        }
        tag.setId(id);
        tagMapper.updateById(tag);
        redisTemplate.delete(CACHE_KEY_TAGS);
    }

    public void delete(Long id) {
        Long count = articleTagMapper.selectCount(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id));
        if (count > 0) {
            throw new BusinessException("该标签还被文章使用，无法删除");
        }
        tagMapper.deleteById(id);
        redisTemplate.delete(CACHE_KEY_TAGS);
    }
}