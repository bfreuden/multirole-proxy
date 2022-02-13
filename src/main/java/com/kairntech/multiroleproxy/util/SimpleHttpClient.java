package com.kairntech.multiroleproxy.util;

import com.kairntech.multiroleproxy.remote.RegisterSpecHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;

import java.util.function.Consumer;

import static com.kairntech.multiroleproxy.remote.RouterHandler.REGISTER_SPEC_URI;

public class SimpleHttpClient {

    static AttributeKey<Consumer<AsyncResult<FullHttpResponse>>> HANDLER_ATTR = AttributeKey.newInstance("handler");

    private final Bootstrap b;
    private final String host;
    private final int port;

    public class SimpleHttpClientRequest {

        private final HttpMethod method;
        private final String uri;
        public final DefaultFullHttpRequest request;

        private SimpleHttpClientRequest(HttpMethod method, String uri, ByteBuf content) {
            this.method = method;
            this.uri = uri;
            this.request = content == null ? new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri) :  new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, content);
            if (content != null && !uri.equals(REGISTER_SPEC_URI)) // WHY ????
                request.headers().add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            this.request.headers().add(HttpHeaderNames.HOST, host+":"+port);
        }

        public void send(Consumer<AsyncResult<FullHttpResponse>> handler) {
            try {
                b.connect(host, port).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel channel = future.channel();
                        channel.attr(HANDLER_ATTR).set(handler);
                        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                        channel.writeAndFlush(request);
                    } else {
                        handler.accept(new AsyncResult<>(future.cause()));
                    }
                });
            } catch (Throwable t) {
                handler.accept(new AsyncResult<>(t));
            }
        }
    }

    public SimpleHttpClient(EventLoopGroup group, String host, int port) {
        this.b = new Bootstrap();
        this.host = host;
        this.port = port;
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new SimpleHttpClientChannelInitializer(host, port));

    }

    public SimpleHttpClientRequest get(String uri) {
        return this.request(HttpMethod.GET, uri, null);
    }

    public SimpleHttpClientRequest post(String uri, ByteBuf content) {
        return this.request(HttpMethod.POST, uri, content);
    }

    public SimpleHttpClientRequest request(HttpMethod method, String uri) {
        return new SimpleHttpClientRequest(method, uri, null);
    }

    public SimpleHttpClientRequest request(HttpMethod method, String uri, ByteBuf content) {
        return new SimpleHttpClientRequest(method, uri, content);
    }

}
