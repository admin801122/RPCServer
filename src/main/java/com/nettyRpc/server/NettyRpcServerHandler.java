package com.nettyRpc.server;

import com.nettyRpc.client.RpcInfo;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Created by zhangshukang.
 */
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
