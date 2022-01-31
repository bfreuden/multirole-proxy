package com.kairntech.multiroleproxy.remote;


import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RemoteProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final Peers peers;

    private static final Logger log = Logger.getLogger( RemoteProxyChannelInitializer.class.getSimpleName());

    public RemoteProxyChannelInitializer(SslContext sslCtx, Peers peers) {
        this.sslCtx = sslCtx;
        this.peers = peers;
    }

    public static ChannelHandler[] handlers() {
        return new ChannelHandler[] {
                new HttpRequestDecoder(),
                new RouterHandler(),
                new HttpObjectAggregator(1048576), // will be removed if non "register peer" call
                new ReconfigureRemotePipelineHandler(), // will be removed if non "register peer" call
                new HttpResponseEncoder(),
                new RegisterPeerHandler(),
                new ForwardClientRequestToLocalProxyHandler()
        };
    }

    public static void reconfigurePipeline(ChannelPipeline p) {
        while (p.last() != null)
            p.removeLast();

        p.addLast(new HttpClientCodec());
//        p.addLast(new HttpRequestEncoder());
        // Remove the following line if you don't want automatic content decompression.
        p.addLast(new HttpContentDecompressor());
        p.addLast(new ForwardLocalProxyResponseToClientHandler());
        // Uncomment the following line if you don't want to handle HttpContents.
        //p.addLast(new HttpObjectAggregator(1048576));

//        p.addLast(new ResponseForwardHandler());
    }

    @Override
    public void initChannel(SocketChannel ch) {
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "client connection accepted: " + ch);
        ch.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) {
                RouterHandler.RouteType routeType = ch.attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
                if (routeType == RouterHandler.RouteType.REGISTER_CLIENT) {
                    log.log(Level.FINEST, "peer connection closed");
                    Peers peers = ch.attr(Peers.PEERS_ATTRIBUTE).get();
                    peers.peerDisconnected(ch);
                } else {
                    log.log(Level.FINEST, "client connection closed");
                }

            }
        });
        ChannelPipeline p = ch.pipeline();
        addAttributes(ch, peers);
        ChannelHandler[] handlers = handlers();
        for (ChannelHandler handler: handlers)
            p.addLast(handler);
    }

    public static void addAttributes(Channel ch, Peers peers) {
        ch.attr(Peers.PEERS_ATTRIBUTE).set(peers);
    }

}