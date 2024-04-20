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
public class RefreshTokenInterceptor implements HandlerInterceptor {


    //从构造器中注入stringRedisTemplate
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    //之前
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {


        //从request 获取请求头 authorization
        String token = request.getHeader("authorization");

        log.error("authorization:",token);

        if (StrUtil.isBlank(token)) {

            return  true;
        }

        String tokens = "login:token:" + token;

        //3.从redis中获取User信息
        Map<Object, Object> map = stringRedisTemplate
                .opsForHash()
                .entries(tokens);


        if (map.isEmpty()) {

            return  true;
        }

        //将map转为userDto对象
        UserDTO userDTO= BeanUtil.fillBeanWithMap(map,new UserDTO(),false);

        //存在  保存用户到ThreadLocal 放行
        UserHolder.saveUser(userDTO);
        //刷新token时间
        stringRedisTemplate.expire(tokens,30, TimeUnit.MINUTES);

        return true;
    }


    //之后
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        //移除ThreadLocal
        UserHolder.removeUser();

    }
}
