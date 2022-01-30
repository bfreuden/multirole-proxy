package com.kairntech.multiroleproxy.local;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayList;
import java.util.List;

public class MultiroleForwardingHandler extends ChannelInboundHandlerAdapter {

    List<Object> messages = new ArrayList<>();

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        for (Object message : messages)
            ReferenceCountUtil.release(message);
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpObject) {
            messages.add(msg);
            System.out.println("receiving http content!");
        } else {
            ReferenceCountUtil.release(msg);
        }
    }
}