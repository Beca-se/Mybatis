package com.zh.learn.interceptor;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;

import java.util.Properties;

/**
 * @author Administrator
 * @Class Name MyInterceptor
 * @Desc TODO
 * @create: 2021-03-23 15:04
 **/
public class MyInterceptor implements Interceptor {

    private Properties properties = new Properties();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        return invocation;
    }

    @Override
    public Object plugin(Object target) {

        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}
