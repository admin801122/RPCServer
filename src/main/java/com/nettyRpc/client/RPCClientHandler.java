package com.nettyRpc.client;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

/**
 * Created by zhangshukang on 2019/4/2.
 */
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
