package com.nettyRpc.client.nettyClientScan;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;

/**
 * Created by zhangshukang.
 */
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