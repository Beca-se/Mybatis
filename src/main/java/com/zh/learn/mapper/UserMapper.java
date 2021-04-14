package com.zh.learn.mapper;

import com.zh.learn.domain.User;
import org.apache.ibatis.session.ResultHandler;

/**
 * @author Administrator
 * @Class Name UserMapper
 * @Desc TODO
 * @create: 2021-03-23 16:29
 **/
public interface UserMapper {

    int save(User user);

    /**
     * 通过id删除数据
     *
     * @param id 数据的id
     * @return 删除到条数
     */
    int delete(long id);

    int test(ResultHandler resultHandler, long id);
}
