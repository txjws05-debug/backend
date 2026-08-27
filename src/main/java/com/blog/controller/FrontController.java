package com.blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.dto.ArticleVO;
import com.blog.dto.CommentRequest;
import com.blog.dto.CommentVO;
import com.blog.entity.Category;
import com.blog.entity.Tag;
import com.blog.service.ArticleService;
import com.blog.service.CategoryService;
import com.blog.service.CommentService;
import com.blog.service.TagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FrontController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CommentService commentService;

    public FrontController(ArticleService articleService, CategoryService categoryService,
                           TagService tagService, CommentService commentService) {
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.commentService = commentService;
    }

    @GetMapping("/articles")
    public Result<Page<ArticleVO>> articles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {
        return Result.success(articleService.listPublic(page, size, categoryId, tagId, keyword));
    }

    @GetMapping("/articles/latest")
    public Result<List<ArticleVO>> latest(@RequestParam(defaultValue = "5") int limit) {
        return Result.success(articleService.listLatest(limit));
    }

    @GetMapping("/articles/{id}")
    public Result<ArticleVO> detail(@PathVariable Long id) {
        return Result.success(articleService.getPublicDetail(id));
    }

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.success(categoryService.listAll());
    }

    @GetMapping("/tags")
    public Result<List<Tag>> tags() {
        return Result.success(tagService.listAll());
    }

    @GetMapping("/articles/{id}/comments")
    public Result<List<CommentVO>> comments(@PathVariable Long id) {
        return Result.success(commentService.listByArticleId(id));
    }

    @PostMapping("/articles/{id}/comments")
    public Result<Void> addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        commentService.addComment(id, request);
        return Result.success();
    }
}
