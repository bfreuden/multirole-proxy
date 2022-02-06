package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.ProxyConfig;
import com.kairntech.multiroleproxy.util.Clients;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

public final class RemoteProxy {

    private final ProxyConfig config;
    private final Peers peers;
    private final Clients clients;

    public RemoteProxy(ProxyConfig config) {
        this.config = config;
        this.peers = new Peers();
        this.clients = new Clients();
    }

    public void start() throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (config.isSsl()) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new RemoteProxyChannelInitializer(sslCtx, peers, clients));

            Channel ch = b.bind(config.getPort()).sync().channel();

            System.err.println("remote proxy running on port " + config.getPort());

            ch.closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}