package com.zh.learn.domain;

import java.time.ZonedDateTime;

/**
 * @author Administrator
 * @Class Name User
 * @Desc
 * @create: 2021-03-24 15:11
 **/
public class User {
    private long userId;
    private String userName;
    private String userNickName;
    private String password;
    private ZonedDateTime lastLoginTime;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserNickName() {
        return userNickName;
    }

    public void setUserNickName(String userNickName) {
        this.userNickName = userNickName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ZonedDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(ZonedDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("User{");
        sb.append("userId=").append(userId);
        sb.append(", userName='").append(userName).append('\'');
        sb.append(", userNickName='").append(userNickName).append('\'');
        sb.append(", password='").append(password).append('\'');
        sb.append(", lastLoginTime=").append(lastLoginTime);
        sb.append('}');
        return sb.toString();
    }
}
