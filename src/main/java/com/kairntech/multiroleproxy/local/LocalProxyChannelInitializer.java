package com.kairntech.multiroleproxy.local;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslContext;

import java.util.function.Consumer;

public class LocalProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final Consumer<Channel> registrationCompleteCallback;
    private final EventLoopGroup group;

    public LocalProxyChannelInitializer(EventLoopGroup group, SslContext sslCtx, Consumer<Channel> registrationCompleteCallback) {
        this.group = group;
        this.sslCtx = sslCtx;
        this.registrationCompleteCallback = registrationCompleteCallback;
    }

    public static ChannelHandler[] handlers(EventLoopGroup group, Consumer<Channel> registrationCompleteCallback) {
        return new ChannelHandler[] {
                new HttpClientCodec(),
                new HttpContentDecompressor(),
                new LocalChannelSwitcherHandler(group, registrationCompleteCallback),
        };
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

//        p.addLast(new ReadTimeoutHandler(5)); // not for debug

        ChannelHandler[] handlers = handlers(group, registrationCompleteCallback);
        for (ChannelHandler handler: handlers)
            p.addLast(handler);
    }
}