package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.controller.BlogController;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.ServletConfig;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.INBOX_KEY;

/**
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    private IUserService userService;



    @Resource
    FollowServiceImpl followService;



    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ServletConfig servletConfig;

    @Override
    public Result getHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::setBlogUser);
        return Result.ok(records);

    }


    private void setBlogUser(Blog blog) {
        Long ownerId = blog.getUserId();
        User blogOwner  = userService.getById(ownerId);
        blog.setName(blogOwner.getNickName());
        blog.setIcon(blogOwner.getIcon());
        // 判断当前用户是否对blog点赞过，并设置blog的isLiked字段
        if (UserHolder.getUser() == null) {
            blog.setIsLike(false);
            return;
        }
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet()
                .score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result getBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("帖子不存在");
        }
        setBlogUser(blog);
        return Result.ok(blog);
    }

    /**
     * 点赞功能
     * 1. 判断用户（先获取用户）是否点赞（根据redis里面的set是否存在userid）
     * 2. 如果已经点赞过了，则取消点赞（修改数据库点赞次数-1，移除set里面的userid）
     * 3. 如果没有点赞过，则修改数据库点赞次数+1，将userid添加到set里面
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 判断用户是否点赞过
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id,
                userId.toString());
        // 如果点赞过，则取消点赞
        if (score != null) {
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id,
                    userId.toString());
            this.update().setSql("liked = liked - 1").eq("id", id).update();
        } else {
            // 没点过赞
            boolean updated = this.update().setSql("liked = liked + 1").eq("id", id).update();
            if (updated) {
                // 使用zset，按照时间戳从小到大排序
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id,
                        userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    /**
     * 根据笔记id查看top5的点赞用户，根据点赞时间排序
     * @param id
     * @return
     */
    @Override
    public Result getBlogLikes(Long id) {
        // 查询redis，获取最早的5个点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 用户id从String转为Long
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        String join = StrUtil.join(",", userIds);
        // 数据库查询用户信息
        List<User> userList = userService.query().in("id", userIds)
                .last("order by field(id," + join + ")").list();
        // 转为VO返回给前端
        List<UserDTO> userDTOS = userList.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 发布笔记，并推送给所有粉丝
     *
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 保存探店博文
        boolean saved = this.save(blog);
        if (!saved) {
            return Result.fail("发布笔记失败");
        }
        // 查询当前用户的所有粉丝
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getFollowUserId, userId);
        List<Follow> followList = followService.list(queryWrapper);
        if (followList == null || followList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 将新的笔记推送给所有粉丝
        for (Follow follow : followList) {
            Long followerId = follow.getUserId();
            // 推送blog的id
            stringRedisTemplate.opsForZSet()
                    .add( INBOX_KEY + followerId, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result getFollowBlog(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();

        // 查询用户的inbox，解析blogId和时间戳
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(INBOX_KEY + userId,
                        0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int retOffset = 0;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Long blogId = Long.valueOf(typedTuple.getValue());
            ids.add(blogId);
            long temp = typedTuple.getScore().longValue();
            if (temp == minTime) {
                retOffset++;
            } else {
                retOffset = 1;
            }
            minTime = Math.min(minTime, temp);
        }
        ScrollResult scrollResult = new ScrollResult();

        String join = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("order by field(id," + join + ")").list();
        blogs.forEach(blog -> setBlogUser(blog));

        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(retOffset);
        return Result.ok(scrollResult);
    }
}
