package com.kairntech.multiroleproxy.local;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class MultiroleForwardingHandler extends ChannelInboundHandlerAdapter {

    private final EventLoopGroup group;
    private final Bootstrap b;
    private final List<HttpObject> messages = new ArrayList<>();
    private volatile Channel localChannel;
    private volatile Channel multiroleChannel;
    private boolean connecting = false;
    private volatile boolean connected = false;

    public MultiroleForwardingHandler(EventLoopGroup group) {
        this.group = group;
        this.b = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new MultiroleClientChannelInitializer(() -> localChannel, null));
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        this.localChannel = ctx.channel();
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        this.localChannel = null;
        for (Object message : messages)
            ReferenceCountUtil.release(message);
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        synchronized (messages) {
            if (!connected && !connecting) {
                connecting = true;
                b.connect("localhost", 12008).addListener((ChannelFutureListener) channelFuture -> {
                    if (channelFuture.isSuccess()) {
                        multiroleChannel = channelFuture.channel();
                        synchronized (messages) {
                            for (HttpObject message : messages) {
                                multiroleChannel.writeAndFlush(message);
                            }
                            connected = true;
                        }
                    } else {
                        write503Response(ctx);
                    }
                });
            }
            if (msg instanceof HttpObject) {
                System.out.println("receiving http content!");
                if (!connected) {
                    messages.add((HttpObject) msg);
                } else {
                    multiroleChannel.writeAndFlush(msg);
                }
            } else {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    private void write503Response(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(503, "local proxy can't connect to multirole"),
                Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}