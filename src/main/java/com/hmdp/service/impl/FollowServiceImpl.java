package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    StringRedisTemplate stringRedisTemplate;


    @Resource
    UserServiceImpl userService;
    /**
     * 当前用户userId关注或者取关followUserId
     * @param followUserId
     * @param isFollow
     * @return
     */
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        // 需要关注
        if (isFollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean saved = this.save(follow);
            if (saved) {
                stringRedisTemplate.opsForSet().add(FOLLOW_KEY + userId, String.valueOf(followUserId));
                return Result.ok();
            }
        } else {
            // 需要取关
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId);
            boolean removed = this.remove(queryWrapper);
            if (removed) {
                stringRedisTemplate.opsForSet().remove(FOLLOW_KEY + userId, String.valueOf(followUserId));
                return Result.ok();
            }
        }
        return Result.fail("follow or unfollow failed");
    }

    /**
     * 查询当前用户userId是否关注了followUserId
     * @param followUserId
     * @return
     */
    public Result isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId);
        int count = this.count(queryWrapper);
        return Result.ok(count == 1);
    }

    /**
     * 查询当前用户userId与targetUserId的共同关注
     * @param targetUserId
     * @return
     */
    public Result getCommonFollow(Long targetUserId) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) return Result.fail("用户未登录");
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(FOLLOW_KEY + userId, FOLLOW_KEY + targetUserId);
        // 无交集，返回空集合
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> commonUserIds = intersect.stream()
                .map(str -> Long.valueOf(str))
                .collect(Collectors.toList());
        List<UserDTO> userDTOList =
                userService.listByIds(commonUserIds)
                        .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                        .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
