package com.nettyRpc.client;

import com.nettyRpc.client.nettyClientScan.NettyRpcClient;

/**
 * Created by zhangshukang.
 */

@NettyRpcClient
public interface UserService {
    String callRpc(String param);
}
