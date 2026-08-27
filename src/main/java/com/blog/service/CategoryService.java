package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.entity.Category;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // 分类列表的缓存 Key（全量分类，不区分参数）
    private static final String CACHE_KEY_CATEGORIES = "blog:categories";
    // 过期时间：1 小时
    private static final long CACHE_TTL_HOURS = 1;
    public CategoryService(CategoryMapper categoryMapper, ArticleMapper articleMapper,
                           RedisTemplate<String, Object> redisTemplate) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
        this.redisTemplate = redisTemplate;
    }
    /**
     * 查所有分类（走缓存）
     * 流程：先查 Redis → 命中直接返回；未命中查 DB → 写回 Redis → 返回
     */
    @SuppressWarnings("unchecked")  // 强转 List<Category> 时压制告警
    public List<Category> listAll() {
        // 1. 先尝试从 Redis 取
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY_CATEGORIES);
        if (cached instanceof List) {
            return (List<Category>) cached;
        }
        // 2. 缓存未命中，查数据库
        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getId));
        // 3. 写入 Redis，1 小时后自动过期
        redisTemplate.opsForValue().set(CACHE_KEY_CATEGORIES, list,
                CACHE_TTL_HOURS, TimeUnit.HOURS);
        return list;
    }

    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    public void create(Category category) {
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getName, category.getName()));
        if (count > 0) {
            throw new BusinessException("分类名称已存在");
        }
        categoryMapper.insert(category);
        // 新增分类后，旧缓存不再准确，直接删掉，下次查询会重新加载
        redisTemplate.delete(CACHE_KEY_CATEGORIES);
    }

    public void update(Long id, Category category) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        category.setId(id);
        categoryMapper.updateById(category);
        redisTemplate.delete(CACHE_KEY_CATEGORIES);
    }

    public void delete(Long id) {
        Long count = articleMapper.selectCount(
                new LambdaQueryWrapper<com.blog.entity.Article>()
                        .eq(com.blog.entity.Article::getCategoryId, id));
        if (count > 0) {
            throw new BusinessException("该分类下还有文章，无法删除");
        }
        categoryMapper.deleteById(id);
        redisTemplate.delete(CACHE_KEY_CATEGORIES);
    }
}