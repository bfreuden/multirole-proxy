package com.kairntech.multiroleproxy.local;

import io.netty.channel.*;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.function.Consumer;

public class LocalChannelSwitcherHandler extends SimpleChannelInboundHandler<Object> {

    private final Consumer<Channel> registrationCompleteCallback;
    private final EventLoopGroup group;

    public LocalChannelSwitcherHandler(EventLoopGroup group, Consumer<Channel> registrationCompleteCallback) {
        this.registrationCompleteCallback = registrationCompleteCallback;
        this.group = group;
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
        // now the channel will receive http request from remote proxy
        p.addLast(new HttpRequestDecoder());
        // and forward them to the multirole
        p.addLast(new MultiroleForwardingHandler(group));
    }
}
