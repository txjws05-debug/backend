package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ArticleRequest {

    @NotBlank(message = "标题不能为空")
    private String title;
    private String summary;
    @NotBlank(message = "内容不能为空")
    private String content;
    private String coverImage;
    private Long categoryId;
    @NotNull(message = "状态不能为空")
    private Integer status;
    private List<Long> tagIds;
}
