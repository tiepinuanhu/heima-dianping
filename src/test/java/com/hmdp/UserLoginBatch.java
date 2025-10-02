package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * 运行程序前请先注释拦截器
 */
@Slf4j
@SpringBootTest
public class UserLoginBatch {
    @Autowired
    private IUserService userService;


    @Resource
    RestTemplate restTemplate;

    @Test
    public void function(){
        String loginUrl = "http://localhost:8081/user/login"; // 替换为实际的登录URL
        String tokenFilePath = "tokens.txt"; // 存储Token的文件路径
        List<User> userList = userService.list();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (User user : userList) {
            String phone = user.getPhone();
            LoginFormDTO loginFormDTO = new LoginFormDTO();
            loginFormDTO.setPhone(phone);
            HttpEntity<String> request = new HttpEntity<>(JSONUtil.toJsonStr(loginFormDTO), headers);
            ResponseEntity<String> postForEntity
                    = restTemplate.postForEntity(loginUrl, request, String.class);
            String body = postForEntity.getBody();
            Result bean = JSONUtil.toBean(body, Result.class);
            String token = (String)bean.getData();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tokenFilePath, true))) {
                writer.write(token);
                writer.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("finished");
    }

}