package com.hmdp.utils;

/**
 * 尝试获取锁
 * 释放锁
 *
 * */
public interface ILock {


    /*
    * timeout 过期时间
    * */
    boolean tryLock(Long timeOut);

    //释放锁

    void unLock();

}
