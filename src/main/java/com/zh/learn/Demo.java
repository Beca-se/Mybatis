package com.zh.learn;

import com.zh.learn.mapper.UserMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Administrator
 **/
public class Demo {
    public static void main(String[] args) throws IOException {
        String resource = "./mybatis-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        SqlSession sqlSession = sqlSessionFactory.openSession(false);
        UserMapper mapper = sqlSession.getMapper(UserMapper.class);
        int delete = mapper.delete(1);
        System.out.println(delete);

    }
}
