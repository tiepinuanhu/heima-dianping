package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();


    private static String token = new String();
    public static void saveUser(UserDTO user){
        tl.set(user);
    }
    public static void saveToken(String token1){
        token = token1;
    }

    public static UserDTO getUser(){
        return tl.get();
    }
    public static String getToken(){
        return token;
    }

    public static void removeUser(){
        tl.remove();
    }
}
