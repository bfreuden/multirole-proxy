package com.kairntech.multiroleproxy.util;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class SimpleHttpServer {


    private static class ServerHandler {
        private final Pattern uriRegex;
        private final HttpMethod method;

        Function<SimpleHttpRequest, FullHttpResponse> requestHandler;

        public ServerHandler(Pattern uriRegex, HttpMethod method, Function<SimpleHttpRequest, FullHttpResponse> requestHandler) {
            this.uriRegex = uriRegex;
            this.method = method;
            this.requestHandler = requestHandler;
        }
    }

    static AttributeKey<Consumer<AsyncResult<FullHttpResponse>>> HANDLER_ATTR = AttributeKey.newInstance("handler");

    private final ServerBootstrap b;
    private final ArrayList<ServerHandler> handlers = new ArrayList<>();

    Function<SimpleHttpRequest, FullHttpResponse> matchingHandler(String uri, HttpMethod method) {
        synchronized (handlers) {
            for (ServerHandler handler : handlers) {
                if (handler.uriRegex.matcher(uri).matches() && method.equals(handler.method))
                    return handler.requestHandler;
            }
        }
        return null;
    }

    public SimpleHttpServer(EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        this.b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new SimpleHttpServerChannelInitializer(this));

    }

    public void addHandler(Pattern uriRegex, HttpMethod method, Function<SimpleHttpRequest, FullHttpResponse> handler) {
        synchronized (handlers) {
            handlers.add(new ServerHandler(uriRegex, method, handler));
        }
    }

    public ChannelFuture listen(int port) {
        ChannelFuture result = b.bind(port);
        result.addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                System.err.println("server started on port " + port);
            } else {
                System.err.println("unable to start server on port port");
            }
        });
        return result;
    }

    public ChannelFuture listenLocally(int port) {
        ChannelFuture result = b.bind("127.0.0.1", port);
        result.addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                System.err.println("server started locally on port " + port);
            } else {
                System.err.println("unable to start server on port port");
            }
        });
        return result;

    }


    public ChannelFuture listen() {
        int freePort = findFreePort();
        return listen(freePort);
    }

    public ChannelFuture listenLocally() {
        int freePort = findFreePort();
        return listenLocally(freePort);
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


}
