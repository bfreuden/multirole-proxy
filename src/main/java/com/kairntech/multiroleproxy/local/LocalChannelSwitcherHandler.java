package com.kairntech.multiroleproxy.local;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.function.Consumer;

public class LocalChannelSwitcherHandler extends SimpleChannelInboundHandler<Object> {

    private final Consumer<Channel> registrationCompleteCallback;

    public LocalChannelSwitcherHandler(Consumer<Channel> registrationCompleteCallback) {
        this.registrationCompleteCallback = registrationCompleteCallback;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            if (response.status().equals(HttpResponseStatus.OK)) {
                reconfigurePipeline(ctx.pipeline());
                registrationCompleteCallback.accept(ctx.channel());
            }
        }
    }

    private void reconfigurePipeline(ChannelPipeline p) {
        while (p.last() != null)
            p.removeLast();
        p.addLast(new HttpRequestDecoder());
        p.addLast(new MultiroleForwardingHandler());
    }
}
