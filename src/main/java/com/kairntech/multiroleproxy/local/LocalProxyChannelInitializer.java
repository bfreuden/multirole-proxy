package com.kairntech.multiroleproxy.local;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslContext;

import java.util.function.Consumer;

public class LocalProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final Consumer<Channel> registrationCompleteCallback;

    public LocalProxyChannelInitializer(SslContext sslCtx, Consumer<Channel> registrationCompleteCallback) {
        this.sslCtx = sslCtx;
        this.registrationCompleteCallback = registrationCompleteCallback;
    }

    public static ChannelHandler[] handlers(Consumer<Channel> registrationCompleteCallback) {
        return new ChannelHandler[] {
                new HttpClientCodec(),
                new HttpContentDecompressor(),
                new LocalChannelSwitcherHandler(registrationCompleteCallback),
        };
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

//        p.addLast(new ReadTimeoutHandler(5)); // not for debug

        ChannelHandler[] handlers = handlers(registrationCompleteCallback);
        for (ChannelHandler handler: handlers)
            p.addLast(handler);
    }
}