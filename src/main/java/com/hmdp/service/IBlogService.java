package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {

    Result queryByid(Long id);

    Result saveBlog(Blog blog);

    Result updateLikeCount(Long id);

    Result queryHotList(Integer current);

    Result queryBloglikes(Long id);
}
