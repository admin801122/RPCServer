package com.nettyRpc.server;


import com.nettyRpc.client.UserService;

/**
 * Created by zhangshukang on 2019/4/2.
 */
public class UserServiceImpl implements UserService {

    @Override
    public String callRpc(String param) {
        System.out.println(param);
        return param;
    }
}
