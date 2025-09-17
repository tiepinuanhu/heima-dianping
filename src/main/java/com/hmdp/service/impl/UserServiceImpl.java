package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
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
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 1. 校验手机号phone
     * 2. 生产验证码
     * 3. 保存验证码到session，一遍后续校验
     * 4. 返回验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号phone
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) {
            return Result.fail("phone is not Invalid");
        }
        // 生产验证码
        String code = RandomUtil.randomNumbers(6);
        // 发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 保存验证码到session
        session.setAttribute("code", code);
        return Result.ok();
    }

    /**
     * 1. 校验手机号
     * 2. 校验验证码
     * 3. 根据手机号查询用户
     *  3.1 用户不存在则注册
     *  3.2 保存到session中
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) {
            return Result.fail("phone is not Invalid");
        }
        String cachedCode = (String) session.getAttribute("code");
        String inputCode = loginForm.getCode();
        // 不一致逻辑
        if (cachedCode == null || !cachedCode.equals(inputCode)) {
            return Result.fail("验证码错误");
        }
        // 根据phone查询用户
        User user = this.query().eq("phone", phone).one();
        // 用户不存在
        if (user == null) {
             user = createUserWithPhone(phone);
        }
        session.setAttribute("user", user);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User r = new User();
        r.setPhone(phone);
        r.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        this.save(r);
        return r;
    }
}
