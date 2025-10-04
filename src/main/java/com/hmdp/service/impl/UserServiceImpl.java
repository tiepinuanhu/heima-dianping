package com.hmdp.service.impl;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    StringRedisTemplate stringRedisTemplate;


    /**
     * 1. 校验手机号phone
     * 2. 生产验证码
     * 3. 保存验证码到session，一遍后续校验
     * 4. 返回验证码
     * 保存code到redis
     * @param phone
     * @return
     */
    @Override
    public Result sendCode(String phone) {
        // 校验手机号phone
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) {
            return Result.fail("phone is not Invalid");
        }
        // 生产验证码
        String code = RandomUtil.randomNumbers(6);
        // 发送验证码
        log.info(code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.SECONDS);
        return Result.ok();
    }

    /**
     * 1. 校验手机号
     * 2. 校验验证码
     * 3. 根据手机号查询用户
     *  3.1 用户不存在则注册
     *  3.2 保存到session中
     * @param loginForm
     * @param
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) {
            return Result.fail("phone is not Invalid");
        }
        String cachedCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
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
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        String jsonStr = JSONUtil.toJsonStr(userDTO);
        // 随机生成token，返回给前端
        String token = UUID.randomUUID().toString(true);

        stringRedisTemplate.opsForValue().set(LOGIN_USER_KEY + token, jsonStr, LOGIN_USER_TTL, TimeUnit.DAYS);
        return Result.ok(token);
    }

    @Override
    public void logout() {
        String token = UserHolder.getToken();
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
    }

    private User createUserWithPhone(String phone) {
        User r = new User();
        r.setPhone(phone);
        r.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        this.save(r);
        return r;
    }
}
