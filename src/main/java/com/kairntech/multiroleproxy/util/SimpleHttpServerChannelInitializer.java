package com.kairntech.multiroleproxy.util;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

class SimpleHttpServerChannelInitializer  extends ChannelInitializer<SocketChannel> {


    private static final Logger log = Logger.getLogger(SimpleHttpServerChannelInitializer.class.getSimpleName());
    private final SimpleHttpServer server;

    public SimpleHttpServerChannelInitializer(SimpleHttpServer server) {
        this.server = server;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        maybeLogFinest(log, () -> "server connection accepted: " + ch);
        ch.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                maybeLogFinest(log, () -> "server connection closed: " + ch);
            }
        });
        ChannelPipeline p = ch.pipeline();
        p.addLast(new HttpRequestDecoder());
        p.addLast(new HttpObjectAggregator(1048576));
        p.addLast(new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //p.addLast(new HttpContentCompressor());
        p.addLast(new SimpleHttpServerHandler(server));
    }

}