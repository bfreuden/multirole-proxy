package com.kairntech.multiroleproxy;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

public class ProxyConfig {

    private boolean ssl = false;
    private int port = 9080;
    private String host = "localhost";

    public boolean isSsl() {
        return ssl;
    }

    public ProxyConfig setSsl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public int getPort() {
        return port;
    }

    public ProxyConfig setPort(int port) {
        this.port = port;
        return this;
    }

    public String getHost() {
        return host;
    }

    public ProxyConfig setHost(String host) {
        this.host = host;
        return this;
    }

}
