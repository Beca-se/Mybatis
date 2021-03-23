/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection.property;

import org.apache.ibatis.reflection.ReflectionException;

import java.util.Locale;

/**
 * @author Clinton Begin
 */
public final class PropertyNamer {

    private PropertyNamer() {
        // Prevent Instantiation of Static Class
    }

    /**
     * 通过isXX setXX getXX获取到字段名
     * @param methodName 方法名
     * @return 属性名
     */
    public static String methodToProperty(String methodName) {
        if (methodName.startsWith("is")) {
            methodName = methodName.substring(2);
        } else if (methodName.startsWith("get") || methodName.startsWith("set")) {
            methodName = methodName.substring(3);
        } else {
            throw new ReflectionException("Error parsing property methodName '" + methodName + "'.  Didn't start with 'is', 'get' or 'set'.");
        }

        if (methodName.length() == 1 || (methodName.length() > 1 && !Character.isUpperCase(methodName.charAt(1)))) {
            methodName = methodName.substring(0, 1).toLowerCase(Locale.ENGLISH) + methodName.substring(1);
        }

        return methodName;
    }

  /**
   *
   * @param methodName 方法名
   * @return true --> 是属性方法
   * 判断是否是属性设置或者获取方法
   */
    public static boolean isProperty(String methodName) {
        return isGetter(methodName) || isSetter(methodName);
    }

  /**
   * 检验是否是set方法
   * @param methodName 方法名
   * @return true --> set方法
   * 检验规则是 以set开始,并且方法名字长度大于3
   */
    public static boolean isGetter(String methodName) {
        return (methodName.startsWith("get") && methodName.length() > 3) || (methodName.startsWith("is") && methodName.length() > 2);
    }

    /**
     * 检验是否是set方法
     * @param methodName 方法名
     * @return true --> set方法
     * 检验规则是 以set开始,并且方法名字长度大于3
     */
    public static boolean isSetter(String methodName) {
        return methodName.startsWith("set") && methodName.length() > 3;
    }

}
