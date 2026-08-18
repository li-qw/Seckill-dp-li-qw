package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    @Transactional
    public Result follow(Long id, Boolean followedUserId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String key = "follows:" + userId;
        if (followedUserId == true) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean issuccess = save(follow);
            if (issuccess) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        }
       else {
        boolean success = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
        if (success) {
            stringRedisTemplate.opsForSet().remove(key, id.toString());
        }}
        return Result.ok();
    }

    @Override
    public Result isfollow(Long id) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        if (count > 0) {
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    @Override
    public Result querysameFollow(Long id) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        Set<String> set = stringRedisTemplate.opsForSet().intersect("follows:" + userId, "follows:" + id);
        if(CollectionUtil.isEmpty(set)){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=set.stream().map(s->Long.valueOf(s)).collect(Collectors.toList());
        UserDTO userDTO=new UserDTO();
        List<User> users = userService.listByIds(ids);
        List<UserDTO> userDTOS = users.stream().map(user1 ->
                BeanUtil.copyProperties(user1, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
