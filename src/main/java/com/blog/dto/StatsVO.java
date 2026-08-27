package com.blog.dto;

import lombok.Data;

@Data
public class StatsVO {

    private Long articleCount;
    private Long categoryCount;
    private Long tagCount;
    private Long commentCount;
    private Long draftCount;
}
