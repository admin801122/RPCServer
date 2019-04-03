package com.nettyRpc;


import com.nettyRpc.client.nettyClientScan.EnableNettyRpcClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Created by zhangshukang.
 */

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