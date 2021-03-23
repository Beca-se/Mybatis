/**
 * Copyright 2009-2017 the original author or authors.
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

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
    private String name;
    private final String indexedName;
    private String index;
    private final String children;

    /**
     * orders[O].items[O].name
     * @param fullname 传入的参数
     */
    public PropertyTokenizer(String fullname) {
        // 寻找第一个.
        int delim = fullname.indexOf('.');
        // 如果找到
        if (delim > -1) {
            // 截取点之前的字符
            name = fullname.substring(0, delim);
            // 截取点之后的字符
            children = fullname.substring(delim + 1);
        } else {
            // 全名
            name = fullname;
            // 没有下一个
            children = null;
        }
        indexedName = name;
        // 查找[
        delim = name.indexOf('[');
        // 如果有中括号
        if (delim > -1) {
            // 下标位置
            index = name.substring(delim + 1, name.length() - 1);
            // 名字
            name = name.substring(0, delim);
        }
    }

    public String getName() {
        return name;
    }

    public String getIndex() {
        return index;
    }

    public String getIndexedName() {
        return indexedName;
    }

    public String getChildren() {
        return children;
    }

    @Override
    public boolean hasNext() {
        return children != null;
    }

    @Override
    public PropertyTokenizer next() {
        return new PropertyTokenizer(children);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
    }
}
