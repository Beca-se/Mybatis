/**
 * Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.reflection;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 参数名字解析器
 */
public class ParamNameResolver {

    public static final String GENERIC_NAME_PREFIX = "param";

    private final boolean useActualParamName;

    /**
     * <p>
     * The key is the index and the value is the name of the parameter.<br />
     * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
     * the parameter index is used. Note that this index could be different from the actual index
     * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
     * </p>
     * <ul>
     * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
     *  if useActualParamName==true, 但是jdk版本在1.8一下或者编译时没有加参数--parameters
     * <li>aMethod(int a, int b) -&gt; {{0, "arg0"}, {1, "arg1"}}</li>
     *   if useActualParamName==true, 但是jdk版本在1.8以上并且编译时加了参数--parameters
     *   * <li>aMethod(int a, int b) -&gt; {{0, "a"}, {1, "b"}}</li>
     *  if useActualParamName==false
     * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
     * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
     * </ul>
     */
    private final SortedMap<Integer, String> names;

    private boolean hasParamAnnotation;


    public ParamNameResolver(Configuration config, Method method) {
        // 检查是否使用真实的参数名配置
        this.useActualParamName = config.isUseActualParamName();
        // 获取方法的入参列表类型
        final Class<?>[] paramTypes = method.getParameterTypes();
        // 获取方法注解 二维数组的原因是 多个入参的每一个入参可以有多个注解 (@Param("") @RequestBody String id, @Param("") long type)
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        final SortedMap<Integer, String> map = new TreeMap<>();
        int paramCount = paramAnnotations.length;
        // get names from @Param annotations
        // 遍历所有的参数列表
        for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
            // 如果是特殊的 跳过
            if (isSpecialParameter(paramTypes[paramIndex])) {
                // skip special parameters
                continue;
            }
            String name = null;
            // 如果有Param注解 使用Param的值
            for (Annotation annotation : paramAnnotations[paramIndex]) {
                if (annotation instanceof Param) {
                    hasParamAnnotation = true;
                    name = ((Param) annotation).value();
                    break;
                }
            }
            if (name == null) {
                // @Param was not specified.
                if (useActualParamName) {
                    name = getActualParamName(method, paramIndex);
                }
                if (name == null) {
                    // use the parameter index as the name ("0", "1", ...)
                    // gcode issue #71
                    name = String.valueOf(map.size());
                }
            }
            map.put(paramIndex, name);
        }
        names = Collections.unmodifiableSortedMap(map);
    }

    private String getActualParamName(Method method, int paramIndex) {
        return ParamNameUtil.getParamNames(method).get(paramIndex);
    }

    /**
     * 是否是特殊的参数  {@link RowBounds} 和  {@link ResultHandler}
     *
     * @param clazz
     * @return
     */
    private static boolean isSpecialParameter(Class<?> clazz) {
        return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
    }

    /**
     * Returns parameter names referenced by SQL providers.
     *
     * @return the names
     */
    public String[] getNames() {
        return names.values().toArray(new String[0]);
    }

    /**
     * <p>
     * A single non-special parameter is returned without a name.
     * Multiple parameters are named using the naming rule.
     * In addition to the default names, this method also adds the generic names (param1, param2,
     * ...).
     * </p>
     *
     * @param args the args
     * @return the named params
     */
    public Object getNamedParams(Object[] args) {
        final int paramCount = names.size();
        // 如果参数是null或者参数个数为0
        if (args == null || paramCount == 0) {
            return null;
            // 如果没有param注解 并且参数只有一个
        } else if (!hasParamAnnotation && paramCount == 1) {
            // 注意 这里使用的是key,key是这个参数在入参中的下标
            Object value = args[names.firstKey()];
            return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
        } else {
            // 如果不止一个有效入参 或者有param注解
            final Map<String, Object> param = new ParamMap<>();
            int i = 0;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                // 这里将names的value做key
                param.put(entry.getValue(), args[entry.getKey()]);
                // add generic param names (param1, param2, ...)
                // 额外支持(param1, param2, ...)
                final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
                // ensure not to overwrite parameter named with @Param
                if (!names.containsValue(genericParamName)) {
                    param.put(genericParamName, args[entry.getKey()]);
                }
                i++;
            }
            return param;
        }
    }

    /**
     * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
     *
     * @param object          a parameter object
     * @param actualParamName an actual parameter name
     *                        (If specify a name, set an object to {@link ParamMap} with specified name)
     * @return a {@link ParamMap}
     * 包装成map,如果参数是collection或者array
     * @since 3.5.5
     */
    public static Object wrapToMapIfCollection(Object object, String actualParamName) {
        if (object instanceof Collection) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("collection", object);
            if (object instanceof List) {
                map.put("list", object);
            }
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        } else if (object != null && object.getClass().isArray()) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("array", object);
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        }
        return object;
    }

}
