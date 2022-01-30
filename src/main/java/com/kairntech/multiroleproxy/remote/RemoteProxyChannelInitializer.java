package com.kairntech.multiroleproxy.remote;


import com.kairntech.multiroleproxy.Peers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;

public class RemoteProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final Peers peers;

    public RemoteProxyChannelInitializer(SslContext sslCtx, Peers peers) {
        this.sslCtx = sslCtx;
        this.peers = peers;
    }

    public static ChannelHandler[] handlers() {
        return new ChannelHandler[] {
                new HttpRequestDecoder(),
                new RouterHandler(),
                new HttpObjectAggregator(1048576),
                new RemoteChannelSwitcherHandler(),
                new HttpResponseEncoder(),
                new RegisterPeerHandler(),
                new LocalProxyForwardHandler()
        };
    }

    @Override
    public void initChannel(SocketChannel ch) {
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