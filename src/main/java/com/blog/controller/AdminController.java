package com.blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.dto.ArticleRequest;
import com.blog.dto.ArticleVO;
import com.blog.dto.CommentVO;
import com.blog.dto.StatsVO;
import com.blog.entity.Category;
import com.blog.entity.Tag;
import com.blog.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CommentService commentService;
    private final StatsService statsService;

    public AdminController(ArticleService articleService, CategoryService categoryService,
                           TagService tagService, CommentService commentService,
                           StatsService statsService) {
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.commentService = commentService;
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public Result<StatsVO> stats() {
        return Result.success(statsService.getStats());
    }

    // ---- 文章 ----
    @GetMapping("/articles")
    public Result<Page<ArticleVO>> articles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(articleService.listAdmin(page, size, status, keyword, false));
    }

    @GetMapping("/articles/{id}")
    public Result<ArticleVO> articleDetail(@PathVariable Long id) {
        return Result.success(articleService.getAdminDetail(id));
    }

    @PostMapping("/articles")
    public Result<Map<String, Long>> createArticle(@Valid @RequestBody ArticleRequest request,
                                                   HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Long id = articleService.create(request, userId);
        Map<String, Long> data = new HashMap<>();
        data.put("id", id);
        return Result.success(data);
    }

    @PutMapping("/articles/{id}")
    public Result<Void> updateArticle(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        articleService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/articles/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        articleService.delete(id);
        return Result.success();
    }

    @GetMapping("/recycle/articles")
    public Result<Page<ArticleVO>> deletedArticles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(articleService.listDeleted(page, size));
    }

    @PutMapping("/articles/{id}/restore")
    public Result<Void> restoreArticle(@PathVariable Long id) {
        articleService.restore(id);
        return Result.success();
    }

    @DeleteMapping("/articles/{id}/permanent")
    public Result<Void> permanentDeleteArticle(@PathVariable Long id) {
        articleService.permanentDelete(id);
        return Result.success();
    }

    // ---- 分类 ----
    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.success(categoryService.listAll());
    }

    @PostMapping("/categories")
    public Result<Void> createCategory(@RequestBody Category category) {
        categoryService.create(category);
        return Result.success();
    }

    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        categoryService.update(id, category);
        return Result.success();
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }

    // ---- 标签 ----
    @GetMapping("/tags")
    public Result<List<Tag>> tags() {
        return Result.success(tagService.listAll());
    }

    @PostMapping("/tags")
    public Result<Void> createTag(@RequestBody Tag tag) {
        tagService.create(tag);
        return Result.success();
    }

    @PutMapping("/tags/{id}")
    public Result<Void> updateTag(@PathVariable Long id, @RequestBody Tag tag) {
        tagService.update(id, tag);
        return Result.success();
    }

    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success();
    }

    // ---- 评论 ----
    @GetMapping("/comments")
    public Result<List<CommentVO>> comments() {
        return Result.success(commentService.listAll());
    }

    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }
}
