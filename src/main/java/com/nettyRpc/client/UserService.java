package com.nettyRpc.client;

import com.nettyRpc.client.nettyClientScan.NettyRpcClient;

/**
 * Created by zhangshukang on 2019/4/2.
 */

@NettyRpcClient
public interface UserService {
    String callRpc(String param);
}
