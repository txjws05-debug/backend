package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BusinessException;
import com.blog.dto.ArticleRequest;
import com.blog.dto.ArticleVO;
import com.blog.dto.TagVO;
import com.blog.entity.*;
import com.blog.mapper.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final UserMapper userMapper;
private final RedisTemplate<String, Object> redisTemplate;

// ===== 缓存相关常量 =====
// 文章详情 Key 模板：blog:article:123
private static final String CACHE_KEY_ARTICLE_DETAIL = "blog:article:";
// 最新文章 Key 模板：blog:articles:latest:5
private static final String CACHE_KEY_ARTICLES_LATEST = "blog:articles:latest:";
// 文章详情缓存 30 分钟
private static final long TTL_DETAIL_MINUTES = 30;
// 最新文章缓存 10 分钟
private static final long TTL_LATEST_MINUTES = 10;

public ArticleService(ArticleMapper articleMapper, CategoryMapper categoryMapper,
                      TagMapper tagMapper, ArticleTagMapper articleTagMapper,
                      UserMapper userMapper, RedisTemplate<String, Object> redisTemplate) {
    this.articleMapper = articleMapper;
    this.categoryMapper = categoryMapper;
    this.tagMapper = tagMapper;
    this.articleTagMapper = articleTagMapper;
    this.userMapper = userMapper;
    this.redisTemplate = redisTemplate;
}

    public Page<ArticleVO> listPublic(int page, int size, Long categoryId, Long tagId, String keyword) {
        return listArticles(page, size, categoryId, tagId, keyword, 1, false);
    }

    public Page<ArticleVO> listAdmin(int page, int size, Integer status, String keyword, boolean includeDeleted) {
        LambdaQueryWrapper<Article> wrapper = buildWrapper(null, null, keyword, status, includeDeleted);
        wrapper.orderByDesc(Article::getCreateTime);
        Page<Article> articlePage = articleMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPaheBatch(articlePage);
    }

    private Page<ArticleVO> listArticles(int page, int size, Long categoryId, Long tagId,
                                         String keyword, Integer status, boolean includeDeleted) {
        LambdaQueryWrapper<Article> wrapper = buildWrapper(categoryId, tagId, keyword, status, includeDeleted);
        wrapper.orderByDesc(Article::getCreateTime);
        Page<Article> articlePage = articleMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPaheBatch(articlePage);
    }

    private LambdaQueryWrapper<Article> buildWrapper(Long categoryId, Long tagId, String keyword,
                                                     Integer status, boolean includeDeleted) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Article::getTitle, keyword);
        }
        if (tagId != null) {
            List<ArticleTag> relations = articleTagMapper.selectList(
                    new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tagId));
            if (relations.isEmpty()) {
                wrapper.eq(Article::getId, -1);
            } else {
                wrapper.in(Article::getId, relations.stream().map(ArticleTag::getArticleId).toList());
            }
        }
        return wrapper;
    }

    private  Page<ArticleVO> convertPaheBatch(Page<Article> articlePage)
    {
        List<Article>  articles =articlePage.getRecords();
        if (articles.isEmpty())
        {
            //空值直接 返回 避免 后序无意义的查询
            Page<ArticleVO> empty=new Page<>(articlePage.getCurrent(),articlePage.getSize(),0);
            empty.setRecords(Collections.emptyList());
            return  empty;
        }
        Set<Long> categoryIds=new HashSet<>();
        Set<Long> authorIds=new HashSet<>();
        List<Long>articleIds=new ArrayList<>();
        for (Article a:articles)
        {
            articleIds.add(a.getId());
            if (a.getCategoryId()!=null)categoryIds.add(a.getCategoryId());
            if (a.getAuthorId()!=null) authorIds.add(a.getAuthorId());

        }
        // ---- 批量查询，转成 Map 方便 O(1) 取值 ----
        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        Map<Long, User> userMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        // 查所有文章-标签关系
        List<ArticleTag> allRelations = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIds));
        // 收集所有 tagId，再批量查 tag
        Set<Long> tagIds = allRelations.stream()
                .map(ArticleTag::getTagId)
                .collect(Collectors.toSet());
        Map<Long, Tag> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        // 按 articleId 分组：Map<文章ID, List<标签ID>>
        Map<Long, List<Long>> articleTagIdsMap = allRelations.stream()
                .collect(Collectors.groupingBy(
                        ArticleTag::getArticleId,
                        Collectors.mapping(ArticleTag::getTagId, Collectors.toList())
                ));
        // ---- 组装 VO ----
        List<ArticleVO> voList = articles.stream()
                .map(a -> toVOBatch(a, categoryMap, userMap, articleTagIdsMap, tagMap, false))
                .collect(Collectors.toList());
        Page<ArticleVO> voPage = new Page<>(articlePage.getCurrent(), articlePage.getSize(), articlePage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
    //这是「从 Map 里取数据」的版本，不再访问数据库：
    private ArticleVO toVOBatch(Article article,
                                Map<Long, Category> categoryMap,
                                Map<Long, User> userMap,
                                Map<Long, List<Long>> articleTagIdsMap,
                                Map<Long, Tag> tagMap,
                                boolean withContent) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);
        if (!withContent) {
            vo.setContent(null);
        }
        // 从 Map 取分类名
        if (article.getCategoryId() != null) {
            Category category = categoryMap.get(article.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        // 从 Map 取作者名
        if (article.getAuthorId() != null) {
            User author = userMap.get(article.getAuthorId());
            if (author != null) {
                vo.setAuthorName(author.getNickname());
            }
        }
        // 从 Map 取标签
        List<Long> tagIdList = articleTagIdsMap.getOrDefault(article.getId(), Collections.emptyList());
        List<TagVO> tags = tagIdList.stream()
                .map(tagMap::get)
                .filter(Objects::nonNull)
                .map(tag -> {
                    TagVO tv = new TagVO();
                    tv.setId(tag.getId());
                    tv.setName(tag.getName());
                    return tv;
                })
                .collect(Collectors.toList());
        vo.setTags(tags);
        return vo;
    }

    /**
     * 把 List<Article> 批量转成 List<ArticleVO>
     * 逻辑和 convertPaheBatch 一模一样，只是输入从 Page 换成 List
     * 抽出来是为了 listLatest 和未来其它「不分页」的列表场景也能复用
     */
    private List<ArticleVO> convertBatchToList(List<Article> articles) {
        if (articles.isEmpty()) {
            return Collections.emptyList();
        }

        // ---- 第 1 步：收集所有需要批量查的 ID ----
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> authorIds = new HashSet<>();
        List<Long> articleIds = new ArrayList<>();
        for (Article a : articles) {
            articleIds.add(a.getId());
            if (a.getCategoryId() != null) categoryIds.add(a.getCategoryId());
            if (a.getAuthorId() != null) authorIds.add(a.getAuthorId());
        }

        // ---- 第 2 步：批量 IN 查询，结果转成 Map 方便 O(1) 取值 ----
        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        Map<Long, User> userMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 一次查所有文章-标签关系
        List<ArticleTag> allRelations = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIds));
        // 收集所有 tagId，再批量查 tag
        Set<Long> tagIds = allRelations.stream()
                .map(ArticleTag::getTagId)
                .collect(Collectors.toSet());
        Map<Long, Tag> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        // 按文章 ID 分组：Map<文章ID, List<标签ID>>
        Map<Long, List<Long>> articleTagIdsMap = allRelations.stream()
                .collect(Collectors.groupingBy(
                        ArticleTag::getArticleId,
                        Collectors.mapping(ArticleTag::getTagId, Collectors.toList())
                ));

        // ---- 第 3 步：内存里拼 VO，全程不访问数据库 ----
        return articles.stream()
                .map(a -> toVOBatch(a, categoryMap, userMap, articleTagIdsMap, tagMap, false))
                .collect(Collectors.toList());
    }


    
  /**
 * 公开文章详情（走缓存）
 *
 * 浏览量处理策略（重点）：
 *   - 不管命中缓存还是未命中，都要调 incrementViewCount 给数据库 +1，保证计数准确
 *   - 返回给前端的 VO 在内存里 +1 即可（命中缓存时改的是反序列化出来的对象，没问题）
 */
public ArticleVO getPublicDetail(Long id) {
    String key = CACHE_KEY_ARTICLE_DETAIL + id;

    // 1. 先查 Redis
    ArticleVO cached = (ArticleVO) redisTemplate.opsForValue().get(key);
    if (cached != null) {
        // 命中：依然要给 DB 浏览量 +1
        articleMapper.incrementViewCount(id);
        // 内存里 VO 也 +1，前端看到的就是点击后的值
        cached.setViewCount(cached.getViewCount() + 1);
        return cached;
    }

    // 2. 未命中：查 DB
    Article article = articleMapper.selectById(id);
    if (article == null || article.getStatus() != 1) {
        throw new BusinessException("文章不存在");
    }
    // 原子 +1，MySQL 行锁保证并发下不丢计数
    articleMapper.incrementViewCount(id);
    // 内存 +1，省一次回查
    article.setViewCount(article.getViewCount() + 1);
    ArticleVO vo = toVO(article, true);

    // 3. 写回 Redis，30 分钟自动过期
    redisTemplate.opsForValue().set(key, vo, TTL_DETAIL_MINUTES, TimeUnit.MINUTES);
    return vo;
}

/**
 * 后台文章详情。
 *
 * 与前台详情的区别：
 *   - 不校验 status：草稿在后台也要能看
 *   - 不加浏览量：后台预览不该污染公开计数
 *   - 不走前台缓存：草稿内容不该进公共缓存（前台缓存键是 blog:article:{id}，
 *     后台看到的内容和前台不是一回事，共用缓存会导致草稿泄漏到前台）
 */
public ArticleVO getAdminDetail(Long id) {
    Article article = articleMapper.selectById(id);
    if (article == null) {
        throw new BusinessException("文章不存在");
    }
    return toVO(article, true);
}

   @Transactional
public Long create(ArticleRequest request, Long authorId) {
    Article article = new Article();
    BeanUtils.copyProperties(request, article);
    article.setAuthorId(authorId);
    article.setViewCount(0);
    articleMapper.insert(article);
    saveTags(article.getId(), request.getTagIds());
    // 新发文章会影响「最新文章」列表
    deleteByPattern(CACHE_KEY_ARTICLES_LATEST + "*");
    return article.getId();
}

@Transactional
public void update(Long id, ArticleRequest request) {
    Article article = articleMapper.selectById(id);
    if (article == null) {
        throw new BusinessException("文章不存在");
    }
    BeanUtils.copyProperties(request, article);
    articleMapper.updateById(article);
    articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
    saveTags(id, request.getTagIds());
    // 改文章会影响详情 + 最新列表
    evictArticleCache(id);
}

public void delete(Long id) {
    if (articleMapper.selectById(id) == null) {
        throw new BusinessException("文章不存在");
    }
    articleMapper.deleteById(id);
    evictArticleCache(id);
}

public void restore(Long id) {
    if (articleMapper.restoreById(id) == 0) {
        throw new BusinessException("文章不存在或未被删除");
    }
    evictArticleCache(id);
}

public void permanentDelete(Long id) {
    articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
    if (articleMapper.physicalDeleteById(id) == 0) {
        throw new BusinessException("文章不存在");
    }
    evictArticleCache(id);
}
    public Page<ArticleVO> listDeleted(int page, int size) {
        Page<Article> articlePage = articleMapper.selectDeletedPage(new Page<>(page, size));
        return convertPaheBatch(articlePage);
    }

    private void saveTags(Long articleId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            ArticleTag at = new ArticleTag();
            at.setArticleId(articleId);
            at.setTagId(tagId);
            articleTagMapper.insert(at);
        }
    }

    private Page<ArticleVO> convertPage(Page<Article> articlePage) {
        Page<ArticleVO> voPage = new Page<>(articlePage.getCurrent(), articlePage.getSize(), articlePage.getTotal());
        voPage.setRecords(articlePage.getRecords().stream()
                .map(a -> toVO(a, false))
                .collect(Collectors.toList()));
        return voPage;
    }

    private ArticleVO toVO(Article article, boolean withContent) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);
        if (!withContent) {
            vo.setContent(null);
        }
        if (article.getCategoryId() != null) {
            Category category = categoryMapper.selectById(article.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        User author = userMapper.selectById(article.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.getNickname());
        }
        vo.setTags(getTagsByArticleId(article.getId()));
        return vo;
    }

    private List<TagVO> getTagsByArticleId(Long articleId) {
        List<ArticleTag> relations = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = relations.stream().map(ArticleTag::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds).stream().map(tag -> {
            TagVO tv = new TagVO();
            tv.setId(tag.getId());
            tv.setName(tag.getName());
            return tv;
        }).collect(Collectors.toList());
    }

    /**
     * 最新文章列表（首页侧边栏用）
     * 优化点：原来循环调 toVO，每篇文章都查 3 次 DB（N+1 问题）
     * 现在改成：先查文章列表 → 复用批量组装逻辑 → 一次返回
     */
 @SuppressWarnings("unchecked")
public List<ArticleVO> listLatest(int limit) {
    String key = CACHE_KEY_ARTICLES_LATEST + limit;

    // 1. 先查 Redis
    Object cached = redisTemplate.opsForValue().get(key);
    if (cached instanceof List) {
        return (List<ArticleVO>) cached;
    }

    // 2. 未命中：查 DB + 批量组装
    LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Article::getStatus, 1)
            .orderByDesc(Article::getCreateTime)
            .last("LIMIT " + limit);
    List<Article> articles = articleMapper.selectList(wrapper);
    List<ArticleVO> voList = convertBatchToList(articles);  // 见第一节给的代码

    // 3. 写回 Redis，10 分钟过期
    redisTemplate.opsForValue().set(key, voList, TTL_LATEST_MINUTES, TimeUnit.MINUTES);
    return voList;
}
/**
 * 清掉某篇文章相关的所有缓存
 *  - 详情缓存：blog:article:{id}
 *  - 最新文章缓存：blog:articles:latest:* （因为 limit 不固定，用 scan 删）
 */
private void evictArticleCache(Long id) {
    // 1. 删该文章的详情缓存
    redisTemplate.delete(CACHE_KEY_ARTICLE_DETAIL + id);
    // 2. 删所有最新文章缓存（limit 可能是 5、10 等，用 SCAN 模式匹配删除）
    deleteByPattern(CACHE_KEY_ARTICLES_LATEST + "*");
}

/**
 * 按 Key 模式批量删除（用 SCAN，避免 KEYS 阻塞 Redis）
 */
private void deleteByPattern(String pattern) {
    var keys = redisTemplate.keys(pattern);  // 项目小，keys 也够用；高并发场景可换 scan
    if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
    }
}
}
