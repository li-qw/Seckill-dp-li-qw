package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
       if(RegexUtils.isPhoneInvalid(phone)){
           return Result.fail("手机号格式不正确");
       }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.绑定验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码
        log.info("验证码发送成功:{}", code);
        //5.返回结果，成功
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号验证码
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式不正确");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginForm.getPhone());
        if(cacheCode==null||!cacheCode.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
       //根据手机号查询用户信息
        User user = query().eq("phone", loginForm.getPhone()).one();
//userMapper.selectOne(new QueryWrapper<User>().eq("phone", loginForm.getPhone()));
        //判断用户是否存在，不存在就注册
        if(user==null){
        user =  User.builder()
                    .phone(loginForm.getPhone())
                    .nickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10))
                    .build();
          save(user);
        }
        //存入 redis
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
        CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((filedName,filedValue)->filedValue==null?null:filedValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,usermap);
        stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        log.info("登录成功:{}", userDTO);
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        //1.获取请求头里面token
        String token = request.getHeader("authorization");
        if(token == null){
            return Result.fail("未登录");
        }
        //2.删除redis中的登录key
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        //3.清除ThreadLocal
        UserHolder.removeUser();
        log.info("退出成功");
        return Result.ok();
    }
}

