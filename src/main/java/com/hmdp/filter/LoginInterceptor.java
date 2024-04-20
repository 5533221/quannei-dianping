package com.hmdp.filter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author 27164
 * @version 1.0
 * @description: TODO  登录的拦截器
 * @date 2024/4/12 20:49
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    //之前
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {


        //判断ThreadLocal是否有用户
        if (UserHolder.getUser()==null) {

            response.setStatus(401);
            return false;
        }
        return true;
    }

}
