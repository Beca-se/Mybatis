package com.zh.learn.mapper;

/**
 * @author Administrator
 * @Class Name UserMapper
 * @Desc TODO
 * @create: 2021-03-23 16:29
 **/
public interface UserMapper {

    /**
     * 通过id删除数据
     *
     * @param id 数据的id
     * @return 删除到条数
     */
    int delete(long id);
}
