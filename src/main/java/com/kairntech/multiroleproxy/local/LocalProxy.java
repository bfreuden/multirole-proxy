package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.ProxyConfig;
import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
//import io.netty.handler.ssl.SslContext;
//import io.netty.handler.ssl.SslContextBuilder;
//import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static com.kairntech.multiroleproxy.local.MultiroleChangeNotifier.X_LOCAL_PROXY_ID_HEADER;
import static com.kairntech.multiroleproxy.local.Multiroles.MULTIROLES_ATTRIBUTE;
import static com.kairntech.multiroleproxy.remote.RouterHandler.REGISTER_CLIENT_URI;

public class LocalProxy {

    private final String id;
    private ProxyConfig config;
    private EventLoopGroup group;
    private NioEventLoopGroup bossGroup;

    public LocalProxy(ProxyConfig config) {
        this.config = config;
        String hostname;
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }
        this.id = System.getProperty("user.name") + "@" + hostname;
    }

    public synchronized void start() {
//        try {
//            final boolean ssl = config.isSsl();
//            final SslContext sslCtx;
//            if (ssl) {
//                sslCtx = SslContextBuilder.forClient()
//                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
//            } else {
//                sslCtx = null;
//            }
            this.bossGroup = new NioEventLoopGroup(1);
            this.group = new NioEventLoopGroup();
            MultiroleChangeNotifier multiroleChangeNotifier = new MultiroleChangeNotifier(group, config, this.id);
            Multiroles multiroles = new Multiroles(group, multiroleChangeNotifier);
            AdminServer adminServer = new AdminServer(bossGroup, group, multiroles, config, multiroleChangeNotifier/*, sslCtx*/, this.id);
            adminServer.start();
//        } catch (SSLException e) {
//            throw new RuntimeException(e);
//        }
    }

    public void stop() {
        if (group != null)
            group.shutdownGracefully();
        if (bossGroup != null)
            bossGroup.shutdownGracefully();
    }

}
