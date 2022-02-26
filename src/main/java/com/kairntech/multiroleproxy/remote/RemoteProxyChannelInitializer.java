package com.kairntech.multiroleproxy.remote;


import com.kairntech.multiroleproxy.util.Clients;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
//import io.netty.handler.ssl.SslContext;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

public class RemoteProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

//    private final SslContext sslCtx;
    private final Peers peers;
    private final Clients clients;

    private static final Logger log = Logger.getLogger( RemoteProxyChannelInitializer.class.getSimpleName());

    public RemoteProxyChannelInitializer(/*SslContext sslCtx,*/ Peers peers, Clients clients) {
//        this.sslCtx = sslCtx;
        this.peers = peers;
        this.clients = clients;
    }

    public static ChannelHandler[] handlers() {
        return new ChannelHandler[] {
                new HttpRequestDecoder(),
                new RouterHandler(),
                new HttpObjectAggregator(1048576), // will be removed if non "register peer" call
                new ReconfigureRemotePipelineHandler(), // will be removed if non "register peer" call
                new HttpResponseEncoder(),
                new RegisterPeerHandler(),
                new RegisterSpecHandler(),
                new OpenAPISpecHandler(),
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
        maybeLogFinest(log, () -> "client connection accepted: " + ch);
        ch.closeFuture().addListener(future -> {
            RouterHandler.RouteType routeType = ch.attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
            if (routeType == RouterHandler.RouteType.REGISTER_CLIENT) {
                log.log(Level.FINEST, "peer connection closed");
                Peers peers = ch.attr(Peers.PEERS_ATTRIBUTE).get();
                peers.peerDisconnected(ch);
            } else {
                log.log(Level.FINEST, "client connection closed");
            }
        });
        ChannelPipeline p = ch.pipeline();
        ch.attr(Peers.PEERS_ATTRIBUTE).set(peers);
        ch.attr(Peers.CLIENTS_ATTRIBUTE).set(clients);
        ChannelHandler[] handlers = handlers();
        for (ChannelHandler handler: handlers)
            p.addLast(handler);
    }

}