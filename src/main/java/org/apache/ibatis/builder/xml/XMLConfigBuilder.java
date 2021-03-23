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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

    private boolean parsed;
    private final XPathParser parser;
    private String environment;
    private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        super(new Configuration());
        ErrorContext.instance().resource("SQL Mapper Configuration");
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    public Configuration parse() {
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        parsed = true;
        // configuration (properties?, settings?, typeAliases?, typeHandlers?, objectFactory?, objectWrapperFactory?, reflectorFactory?, plugins?, environments?, databaseIdProvider?, mappers?)>
        //  <configuration> 解析这个标签
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    private void parseConfiguration(XNode root) {
        try {
            // issue #117 read properties first
            // 加载配置文件中配置的参数到{@link Configuration#variables 和XPathParser#variables  }
            propertiesElement(root.evalNode("properties"));
            // 加载    <settings>标签
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            //   <typeAliases>
            typeAliasesElement(root.evalNode("typeAliases"));
            //   <typeHandlers>
            typeHandlerElement(root.evalNode("typeHandlers"));
            //  <plugins>
            pluginElement(root.evalNode("plugins"));
            // 使用默认的就可以了
            objectFactoryElement(root.evalNode("objectFactory"));
            // 使用默认的就可以了
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            // 使用default就可以了 没必要配置
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            settingsElement(settings);
            // read it after objectFactory and objectWrapperFactory issue #631
            environmentsElement(root.evalNode("environments"));
            //    <databaseIdProvider type="fsdaf"> 对不同的数据库厂商实现不同的sql 先忽略
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            // 另一个核心文件 mapper.xml <mappers>
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    private Properties settingsAsProperties(XNode context) {
        if (context == null) {
            return new Properties();
        }
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        // 检验配置参数的key是否有效
        for (Object key : props.keySet()) {
            if (!metaConfig.hasSetter(String.valueOf(key))) {
                throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
            }
        }
        return props;
    }

    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String typeAliasPackage = child.getStringAttribute("name");
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }

    private void pluginElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                String interceptor = child.getStringAttribute("interceptor");
                // 获取拦截器的属性
                Properties properties = child.getChildrenAsProperties();
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
                interceptorInstance.setProperties(properties);
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(properties);
            configuration.setObjectFactory(factory);
        }
    }

    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    private void reflectorFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setReflectorFactory(factory);
        }
    }

    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            // 首先获取 <properties resource="./mybatis.properties">内部配置的参数
            Properties defaults = context.getChildrenAsProperties();
            // 检查是否有配置外部资源文件
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            // 不能同时使用resource和url.(不是很明白为什么)
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            if (resource != null) {
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            Properties vars = configuration.getVariables();
            if (vars != null) {
                defaults.putAll(vars);
            }
            parser.setVariables(defaults);
            configuration.setVariables(defaults);
        }
    }

    private void settingsElement(Properties props) throws ClassNotFoundException {
        // 3.5.5 27个配置参数
        // 指定 MyBatis 应如何自动映射列到字段或属性。 NONE 表示关闭自动映射；PARTIAL 只会自动映射没有定义嵌套结果映射的字段。 FULL 会自动映射任何复杂的结果集（无论是否嵌套）
        configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
        // 指定发现自动映射目标未知列（或未知属性类型）的行为。
        //NONE: 不做任何反应
        //WARNING: 输出警告日志（'org.apache.ibatis.session.AutoMappingUnknownColumnBehavior' 的日志等级必须设置为 WARN）
        //FAILING: 映射失败 (抛出 SqlSessionException)
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
        // 	全局性地开启或关闭所有映射器配置文件中已配置的任何缓存。 这个缓存就是指mybatis的二级缓存
        configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
        // 指定 Mybatis 创建可延迟加载对象所用到的代理工具。 CGLIB | JAVASSIST	默认值为JAVASSIST
        configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory", "JAVASSIST")));
        // 延迟加载的全局开关。当开启时，所有关联对象都会延迟加载。 特定关联关系中可通过设置 fetchType 属性来覆盖该项的开关状态。
        configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
        // 开启时，任一方法的调用都会加载该对象的所有延迟加载属性。 否则，每个延迟加载属性会按需加载（参考 lazyLoadTriggerMethods)。
        configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
        // 	是否允许单个语句返回多结果集（需要数据库驱动支持）。	true | false	true
        configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
        // 使用列标签代替列名。实际表现依赖于数据库驱动，具体可参考数据库驱动的相关文档，或通过对比测试来观察。
        configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
        // 允许 JDBC 支持自动生成主键，需要数据库驱动支持。如果设置为 true，将强制使用自动生成主键。尽管一些数据库驱动不支持此特性，但仍可正常工作（如 Derby）。
        configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
        // 	配置默认的执行器。SIMPLE 就是普通的执行器；REUSE 执行器会重用预处理语句（PreparedStatement）； BATCH 执行器不仅重用语句还会执行批量更新。
        configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
        // 	设置超时时间，它决定数据库驱动等待数据库响应的秒数。	任意正整数
        configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
        // 为驱动的结果集获取数量（fetchSize）设置一个建议值。此参数只可以在查询设置中被覆盖。
        configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
        // 	指定语句默认的滚动策略。（新增于 3.5.2）	FORWARD_ONLY | SCROLL_SENSITIVE | SCROLL_INSENSITIVE | DEFAULT（等同于未设置）
        configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
        // 是否开启驼峰命名自动映射，即从经典数据库列名 A_COLUMN 映射到经典 Java 属性名 aColumn。
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        // 是否允许在嵌套语句中使用分页（RowBounds）。如果允许使用则设置为 false。
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        // 	MyBatis 利用本地缓存机制（Local Cache）防止循环引用和加速重复的嵌套查询。 默认值为 SESSION，会缓存一个会话中执行的所有查询。 若设置值为 STATEMENT，本地缓存将仅用于执行语句，对相同 SqlSession 的不同查询将不会进行缓存。
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        // 当没有为参数指定特定的 JDBC 类型时，空值的默认 JDBC 类型。 某些数据库驱动需要指定列的 JDBC 类型，多数情况直接用一般类型即可，比如 NULL、VARCHAR 或 OTHER。
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        // 指定对象的哪些方法触发一次延迟加载。
        configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        // 是否允许在嵌套语句中使用结果处理器（ResultHandler）。如果允许使用则设置为 false。	true
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        // 	指定动态 SQL 生成使用的默认脚本语言。
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        // 指定 Enum 使用的默认 TypeHandler 。（新增于 3.4.5）	一个类型别名或全限定类名。
        configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
        //指定当结果集中值为 null 的时候是否调用映射对象的 setter（map 对象时为 put）方法，这在依赖于 Map.keySet() 或 null 值进行初始化时比较有用。注意基本类型（int、boolean 等）是不能设置成 null 的。
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        // 	允许使用方法签名中的名称作为语句参数名称。 为了使用该特性，你的项目必须采用 Java 8 编译，并且加上 -parameters 选项。（新增于 3.4.1）
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        // 当返回行的所有列都是空时，MyBatis默认返回 null。 当开启这个设置时，MyBatis会返回一个空实例。 请注意，它也适用于嵌套的结果集（如集合或关联）。（新增于 3.4.2）
        configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        // 指定 MyBatis 增加到日志名称的前缀。
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        // 指定一个提供 Configuration 实例的类。 这个被返回的 Configuration 实例用来加载被反序列化对象的延迟加载属性值。 这个类必须包含一个签名为static Configuration getConfiguration() 的方法。（新增于 3.2.3）
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
        // 从SQL中删除多余的空格字符。请注意，这也会影响SQL中的文字字符串。 (新增于 3.5.5)
        configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
        // 指定 MyBatis 所用日志的具体实现，未指定时将自动查找。	SLF4J | LOG4J | LOG4J2 | JDK_LOGGING | COMMONS_LOGGING | STDOUT_LOGGING | NO_LOGGING	未设置
        configuration.setLogImpl(resolveClass(props.getProperty("logImpl")));
        String value = props.getProperty("vfsImpl");
        if (value != null) {
            String[] clazzes = value.split(",");
            for (String clazz : clazzes) {
                if (!clazz.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
                    configuration.setVfsImpl(vfsImpl);
                }
            }
        }

    }

    private void environmentsElement(XNode context) throws Exception {
        if (context != null) {
            if (environment == null) {
                environment = context.getStringAttribute("default");
            }
            for (XNode child : context.getChildren()) {
                String id = child.getStringAttribute("id");
                if (isSpecifiedEnvironment(id)) {
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                    DataSource dataSource = dsFactory.getDataSource();
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    configuration.setEnvironment(environmentBuilder.build());
                }
            }
        }
    }

    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            String type = context.getStringAttribute("type");
            // awful patch to keep backward compatibility
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    private void typeHandlerElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String typeHandlerPackage = child.getStringAttribute("name");
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                        } else {
                            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                        }
                    } else {
                        typeHandlerRegistry.register(typeHandlerClass);
                    }
                }
            }
        }
    }

    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String mapperPackage = child.getStringAttribute("name");
                    configuration.addMappers(mapperPackage);
                } else {
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url == null && mapperClass != null) {
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }

    /**
     * 检验是否是当前设置的default 环境
     * @param id 环境Id
     * @return true 是当前环境
     */
    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        } else if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        } else if (environment.equals(id)) {
            return true;
        }
        return false;
    }

}
