### 概述
大致的流程：

- 实现客户端 代理类处理逻辑 ：InvocationHandler
- 扫描被代理接口，生成代理类，注入 spring 容器
- 根据调用的接口，找到指定的实现类，并完成调用。

### 代码



#### 扫描组件

启动类：
```
@SpringBootApplication
@EnableNettyRpcClient(basePackages = {"com.nettyRpc"})
public class NettyRpcSpringBootApplication implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    public static void main(String[] args) {

        ConfigurableApplicationContext run = SpringApplication.run(NettyRpcSpringBootApplication.class);
    }
    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        factory.setPort(9999);
    }
}
```

自定义扫描注解 EnableNettyRpcClient ：
```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(NettyRpcClientRegistrar.class)
public @interface EnableNettyRpcClient {

    //扫描的包名，如果为空，则根据启动类所在的包名扫描
    String[] basePackages() default {};

    //扫描的字节码类型，可根据字节码类型获取对应的包路径
    Class<?>[] basePackageClasses() default {};
}
```

扫描实现类 NettyRpcClientRegistrar：
```
public class NettyRpcClientRegistrar implements ImportBeanDefinitionRegistrar, BeanClassLoaderAware {


    private ClassLoader classLoader;

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }


    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        ClassPathScanningCandidateComponentProvider scan = getScanner();

        //指定注解，类似于Feign注解
        scan.addIncludeFilter(new AnnotationTypeFilter(NettyRpcClient.class));

        Set<BeanDefinition> candidateComponents = new HashSet<>();
        for (String basePackage : getBasePackages(importingClassMetadata)) {
            candidateComponents.addAll(scan.findCandidateComponents(basePackage));
        }
        candidateComponents.stream().forEach(beanDefinition -> {
            if (!registry.containsBeanDefinition(beanDefinition.getBeanClassName())) {
                if (beanDefinition instanceof AnnotatedBeanDefinition) {
                    AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
                AnnotationMetadata annotationMetadata = annotatedBeanDefinition.getMetadata();
                Assert.isTrue(annotationMetadata.isInterface(), "@NettyRpcClient can only be specified on an interface");
                Map<String, Object> attributes = annotationMetadata.getAnnotationAttributes(NettyRpcClient.class.getCanonicalName());

                this.registerNettyRpcClient(registry, annotationMetadata,attributes);
            }
            }
        });
    }

    private void registerNettyRpcClient(BeanDefinitionRegistry registry,
                                     AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
        String className = annotationMetadata.getClassName();
        BeanDefinitionBuilder definition = BeanDefinitionBuilder
                .genericBeanDefinition(NettyRpcClientFactoryBean.class);
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        definition.addPropertyValue("type", className);
        String name = attributes.get("name") == null ? "" :(String)(attributes.get("name"));
        String alias = name + "NettyRpcClient";
        AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
        beanDefinition.setPrimary(true);
        BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className,
                new String[] { alias });
        BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
    }



    protected ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    AnnotatedBeanDefinition beanDefinition) {
                if (beanDefinition.getMetadata().isIndependent()) {
                    // 判断接口是否继承了 Annotation注解
                    if (beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata()
                            .getInterfaceNames().length == 1 && Annotation.class.getName().equals(beanDefinition.getMetadata().getInterfaceNames()[0])) {
                        try {
                            Class<?> target = ClassUtils.forName(beanDefinition.getMetadata().getClassName(),
                                    NettyRpcClientRegistrar.this.classLoader);
                            return !target.isAnnotation();
                        } catch (Exception ex) {
                            this.logger.error(
                                    "Could not load target class: " + beanDefinition.getMetadata().getClassName(), ex);
                        }
                    }
                    return true;
                }
                return false;

            }
        };
    }



    protected Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(EnableNettyRpcClient.class.getCanonicalName());

        Set<String> basePackages = new HashSet<>();
        for (String pkg : (String[]) attributes.get("basePackages")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }
        for (Class<?> clazz : (Class[]) attributes.get("basePackageClasses")) {
            basePackages.add(ClassUtils.getPackageName(clazz));
        }

        if (basePackages.isEmpty()) {
            basePackages.add(
                    ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }
        return basePackages;
    }
}

```


扫描代理工厂类：NettyRpcClientFactoryBean

```
@Data
@EqualsAndHashCode(callSuper = false)
public class NettyRpcClientFactoryBean implements FactoryBean<Object>{

    private Class<?> type;
    @Override
    public Object getObject() throws Exception {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new NettyRpcInvocationHandler(type));
    }

    @Override
    public Class<?> getObjectType() {
        return this.type;
    }
}
```


请求拦截实现类：NettyRpcInvocationHandler
```
public class NettyRpcInvocationHandler implements InvocationHandler {

    private Class<?> type;

    public NettyRpcInvocationHandler(Class<?> type){
        this.type = type;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        //构造调用信息
        RpcInfo rpcInfo = new RpcInfo();
        rpcInfo.setClassName(type.getName());
        rpcInfo.setMethodName(method.getName());
        rpcInfo.setParamTypes(method.getParameterTypes());
        rpcInfo.setParams(args);

        //使用netty发送调用信息给服务提供方
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        RPCClientHandler rpcClientHandler = new RPCClientHandler();
        try {
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ObjectEncoder());
                            //反序列化对象时指定类解析器，null表示使用默认的类加载器
                            ch.pipeline().addLast(new ObjectDecoder(1024 * 64, ClassResolvers.cacheDisabled(null)));
                            ch.pipeline().addLast(rpcClientHandler);

                        }
                    });
            //connect是异步的，但调用其future的sync则是同步等待连接成功
            ChannelFuture future = bootstrap.connect("127.0.0.1", 80).sync();
            //同步等待调用信息发送成功
            future.channel().writeAndFlush(rpcInfo).sync();
            //同步等待RPCClientHandler的channelRead被触发后（意味着收到了调用结果）
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }

        //返回调用结果
        return rpcClientHandler.getRpcResult();
    }
}
```

-------

#### 客户端
Netty 客户端 ChannelHandler：： RPCClientHandler

```
public class RPCClientHandler extends ChannelHandlerAdapter {

    /**
     * RPC调用返回的结果
     */
    private Object rpcResult;

    public Object getRpcResult() {
        return rpcResult;
    }

    public void setRpcResult(Object rpcResult) {
        this.rpcResult = rpcResult;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        setRpcResult(msg);
        ctx.close();
    }
}
```

请求实体 RpcInfo：
```
@Data
public class RpcInfo implements Serializable {

    /**
     * 调用服务的接口名
     */
    private String className;
    /**
     * 调用服务的方法名
     */
    private String methodName;
    /**
     * 调用方法的参数列表类型
     */
    private Class[] paramTypes;
    /**
     * 调用服务传参
     */
    private Object[] params;
}
```

-----

#### 服务端
NettyServer：

```
public class NettyRpcServer {

    public static void main(String[] args){
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        try {
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ObjectEncoder());
                            ch.pipeline().addLast(new ObjectDecoder(1024 * 64, ClassResolvers.cacheDisabled(null)));
                            ch.pipeline().addLast(new NettyRpcServerHandler());
                        }
                    });
            //bind初始化端口是异步的，但调用sync则会同步阻塞等待端口绑定成功
            ChannelFuture future = bootstrap.bind(80).sync();
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
```

Netty 服务端 ChannelHandler：
```
public class NettyRpcServerHandler extends ChannelHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //获取调用信息，寻找服务实现类
        RpcInfo rpcInfo = (RpcInfo) msg;
        String implName = getImplClassName(rpcInfo.getClassName());
        Class<?> clazz = Class.forName(implName);
        Method method = clazz.getMethod(rpcInfo.getMethodName(), rpcInfo.getParamTypes());
        Object result = method.invoke(clazz.newInstance(), rpcInfo.getParams());
        ctx.writeAndFlush(result);
    }

    private String getImplClassName(String interfaceName) throws ClassNotFoundException {
        Class interClass = Class.forName(interfaceName);
        String servicePath = "com.nettyRpc.server";
        Reflections reflections = new Reflections(servicePath);
        Set<Class> implClasses = reflections.getSubTypesOf(interClass);
        if (implClasses.isEmpty()) {
            System.err.println("impl class is not found!");
        } else if (implClasses.size() > 1) {
            System.err.println("there are many impl classes, not sure invoke which");
        } else {
            Class[] classes = implClasses.toArray(new Class[1]);
            return classes[0].getName();
        }
        return null;
    }
}
```


-------

#### 请求示例：
代理service：
```
@NettyRpcClient
public interface UserService {
    String callRpc(String param);
}
```

服务端具体实现类：
```
public class UserServiceImpl implements UserService {

    @Override
    public String callRpc(String param) {
        System.out.println(param);
        return param;
    }
}
```

远程调用：
```
@RestController
public class UserController {

    @Autowired
    UserService userService;

    @RequestMapping(value = "/callRpc")
    public String callRpcTest(){
        userService.callRpc("callRpc execute......");
        return "ok";
    }
}
```



-----

项目地址：https://github.com/admin801122/RPCServer
