package com.kairntech.multiroleproxy.local;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocalProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final Consumer<Channel> registrationCompleteCallback;
    private final EventLoopGroup group;

    private static final Logger log = Logger.getLogger(LocalProxyChannelInitializer.class.getSimpleName());

    public LocalProxyChannelInitializer(EventLoopGroup group, SslContext sslCtx, Consumer<Channel> registrationCompleteCallback) {
        this.group = group;
        this.sslCtx = sslCtx;
        this.registrationCompleteCallback = registrationCompleteCallback;
    }

    public static ChannelHandler[] handlers(EventLoopGroup group, Consumer<Channel> registrationCompleteCallback) {
        return new ChannelHandler[] {
                new HttpClientCodec(),
                new HttpContentDecompressor(),
                new ReconfigureLocalPipelineHandler(group, registrationCompleteCallback),
        };
    }

    public static void reconfigurePipeline(ChannelPipeline p, EventLoopGroup group) {
        while (p.last() != null)
            p.removeLast();
        // now the channel will receive http request from remote proxy
        //FIXME should have a response decoder
        p.addLast(new HttpRequestDecoder());
        p.addLast(new HttpResponseEncoder());
        // and forward them to the multirole
        p.addLast(new ForwardRemoteRequestToMultiroleHandler(group));
    }

    @Override
    public void initChannel(SocketChannel ch) {
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "connection established with remote proxy: " + ch);
        ch.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) {
                log.log(Level.FINEST, "remote proxy connection closed");
            }
        });
        ChannelPipeline p = ch.pipeline();

//        p.addLast(new ReadTimeoutHandler(5)); // not for debug

        ChannelHandler[] handlers = handlers(group, registrationCompleteCallback);
        for (ChannelHandler handler: handlers)
            p.addLast(handler);
    }
}