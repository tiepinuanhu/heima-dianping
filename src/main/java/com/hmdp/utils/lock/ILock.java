package com.hmdp.utils.lock;

public interface ILock {

    /**
     * 尝试获取锁，如果失败了就不再获取
     * @param timeoutSec 锁持有的超时时间
     * @return 返回获取锁成功或者失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();

}
