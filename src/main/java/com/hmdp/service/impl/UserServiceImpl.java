package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
       if(RegexUtils.isPhoneInvalid(phone)){
           return Result.fail("手机号格式不正确");
       }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.绑定验证码到session
        session.setAttribute("code", code);
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
        Object cacheCode = session.getAttribute("code");
        if(cacheCode==null||!cacheCode.toString().equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
       //根据手机号查询用户信息
        User user = query().eq("phone", loginForm.getPhone()).one();
//userMapper.selectOne(new QueryWrapper<User>().eq("phone", loginForm.getPhone()));
        //判断用户是否存在，不存在就注册，然后都存在session里
        if(user==null){
        user =  User.builder()
                    .phone(loginForm.getPhone())
                    .nickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10))
                    .build();
          save(user);
        }
        //将 User 转为 UserDTO，只暴露必要字段存入 session
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setNickName(user.getNickName());
        userDTO.setIcon(user.getIcon());
        session.setAttribute("user", userDTO);
        log.info("登录成功:{}", userDTO);
        return Result.ok();
    }
}

