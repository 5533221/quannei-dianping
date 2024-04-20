package com.hmdp.utils;

import com.hmdp.entity.Shop;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
