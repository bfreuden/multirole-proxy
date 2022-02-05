package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static com.kairntech.multiroleproxy.local.MultiroleChangeNotifier.X_LOCAL_PROXY_ID_HEADER;
import static com.kairntech.multiroleproxy.remote.RouterHandler.REGISTER_CLIENT_URI;

public class LocalProxy {

    private final String id;
    private ProxyConfig config;
    private EventLoopGroup group;
    private Channel channel;
    private boolean displayErrorMessage = true;
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
        try {
            final boolean ssl = config.isSsl();
            final SslContext sslCtx;
            if (ssl) {
                sslCtx = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } else {
                sslCtx = null;
            }
            this.bossGroup = new NioEventLoopGroup(1);
            this.group = new NioEventLoopGroup();
            MultiroleChangeNotifier multiroleChangeNotifier = new MultiroleChangeNotifier(group, config, this.id);
            Multiroles multiroles = new Multiroles(group, multiroleChangeNotifier);
            AdminServer adminServer = new AdminServer(bossGroup, group, multiroles);
            adminServer.start();
            if (true)
                return;
            Bootstrap b = new Bootstrap();
            System.out.println("connecting to remote proxy at " + config.getHost() + ":" + config.getPort() + "...");
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new LocalProxyChannelInitializer(group, sslCtx, c -> {
                        System.out.println("local proxy connected to remote proxy!");
                        displayErrorMessage = true;
                        channel = c;
                    }));
            while (true) {
                try {
                    if (channel == null) {
                        Channel channel = b.connect(this.config.getHost(), this.config.getPort()).sync().channel();
                        HttpRequest request = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_1, HttpMethod.GET, REGISTER_CLIENT_URI, Unpooled.EMPTY_BUFFER);
                        request.headers().set(HttpHeaderNames.HOST, this.config.getHost() + ":" + this.config.getPort());
                        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                        request.headers().set(X_LOCAL_PROXY_ID_HEADER, this.id);
                        channel.writeAndFlush(request);
                        channel.closeFuture().addListener(e -> {
                            this.channel = null;
                            multiroleChangeNotifier.remoteProxyConnected(false);
                        });
                        multiroleChangeNotifier.remoteProxyConnected(true);
                    }
                } catch (Exception e) {
                    if (displayErrorMessage) {
                        System.err.println("remote proxy connection failed!");
                        System.err.println("will retry every 30 seconds from now...");
                        displayErrorMessage = false;
                    }
                    if (!(e instanceof ConnectException)) {
                        if (channel != null)
                            channel.close();
                    }
                    channel = null;
                } finally {
                    wait(30000L);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (group != null)
            group.shutdownGracefully();
        if (bossGroup != null)
            bossGroup.shutdownGracefully();
    }

}
