package com.hmdp.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.service.impl.FollowServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    FollowServiceImpl followService;

    /**
     * 当前用户关注或取关followUserId
     * @param followUserId
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }


    @GetMapping("or/not/{id}")
    public Result isFollowed(@PathVariable("id") Long followUserId) {
        return followService.isFollowed(followUserId);
    }


    @GetMapping("common/{id}")
    public Result getCommonFollow(@PathVariable("id") Long targetUserId) {
        return followService.getCommonFollow(targetUserId);
    }

}
