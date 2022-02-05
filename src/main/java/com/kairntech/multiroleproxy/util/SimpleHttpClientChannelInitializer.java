package com.kairntech.multiroleproxy.util;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

class SimpleHttpClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger log = Logger.getLogger( SimpleHttpClientChannelInitializer.class.getSimpleName().replace("Handler", "") );

    private final String host;
    private final int port;

    public SimpleHttpClientChannelInitializer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        maybeLogFinest(log, () -> "http client connected to" + host + ":" + port +" " + ch);
        ch.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                maybeLogFinest(log, () -> "http client connection closed: " + ch);
            }
        });
        ChannelPipeline p = ch.pipeline();
        p.addLast(new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        //p.addLast(new HttpObjectAggregator(1048576));
        p.addLast(new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //p.addLast(new HttpContentCompressor());
        p.addLast(new SimpleHttpClientHandler());

    }
}
