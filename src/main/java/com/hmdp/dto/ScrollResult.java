package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页的返回结果
 */
@Data
public class ScrollResult {
    // 笔记列表
    private List<?> list;
    // 上传的最小时间戳
    private Long minTime;
    // 上次最小时间戳相同的笔记个数
    private Integer offset;
}
