package com.kairntech.multiroleproxy.local;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminRequestsServerChannelInitializer  extends ChannelInitializer<SocketChannel> {


    private static final Logger log = Logger.getLogger( AdminRequestsServerChannelInitializer.class.getSimpleName());

    private final AdminServer multiroles;

    public AdminRequestsServerChannelInitializer(AdminServer multiroles) {
        this.multiroles = multiroles;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "admin command connection accepted: " + ch);
        ch.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "admin command connection closed: " + ch);
            }
        });
        ChannelPipeline p = ch.pipeline();
        p.addLast(new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        //p.addLast(new HttpObjectAggregator(1048576));
        p.addLast(new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //p.addLast(new HttpContentCompressor());
        p.addLast(new AdminCommandHandler(multiroles));
    }

}