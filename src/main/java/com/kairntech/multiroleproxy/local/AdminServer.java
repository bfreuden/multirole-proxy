package com.kairntech.multiroleproxy.local;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

public class AdminServer {

    private final int port;
    private final NioEventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public AdminServer(NioEventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        this.port = findFreePort();
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
    }

    public void start() {
//        new Thread(() -> {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new AdminRequestsServerChannelInitializer(AdminServer.this));

            b.bind(port).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    if (channelFuture.isSuccess()) {
                        System.err.println("local proxy listening to admin request on port " + port);
                    } else {
                        System.err.println("unable to start server listening to admin requests");
                    }
                }
            });

//        }).start();
    }

    public int getAdminPort() {
        return this.port;
    }

    private int findFreePort() {
        int port = 30000;
        int maxPort = 30100;
        while (port < maxPort) {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                serverSocket.close();
                break;
            } catch (IOException e) {
                port++;
                if (port == maxPort)
                    throw new RuntimeException("Unable to find a free port to listen for admin requests");
            }
        }
        return port;
    }

    public List<String> getMultiroleAsStrings() {
        return null;
    }

    public void addMultiroleServers(List<String> arguments) {
    }

    public void deleteMultiroleServers(List<String> arguments) {
    }
}
