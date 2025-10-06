package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 60L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 1L;

    public static final String CACHE_SHOP_TYPES_KEY = "shop-types";

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "liked:blog:";
    public static final String INBOX_KEY = "inbox:user:";
    public static final String SHOP_GEO_KEY = "geo:shop-type:";
    public static final String USER_SIGN_KEY = "sign:";

    public static final String FOLLOW_KEY = "follow:user:";
}
