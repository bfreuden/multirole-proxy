package com.kairntech.multiroleproxy.remote;

import io.netty.channel.*;
import io.netty.util.AttributeKey;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ForwardLocalProxyResponseToClientHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Channel> CLIENT_CHANNEL_ATTRIBUTE = AttributeKey.newInstance("clientChannel");

    private static final Logger log = Logger.getLogger( ForwardLocalProxyResponseToClientHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "sending data back to the client...: " + ctx.channel() + " " + msg);
        Channel clientChannel = ctx.channel().attr(CLIENT_CHANNEL_ATTRIBUTE).get();
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "client channel is...: " + clientChannel);
//        clientChannel.close();
        clientChannel.writeAndFlush(msg);
                //.addListener(ChannelFutureListener.CLOSE);
//        ctx.fireChannelRead(msg);
    }

//    @Override
//    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
//        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "sending data back to the client: " + ctx.channel() + " " + msg);
//        Channel clientChannel = ctx.channel().attr(CLIENT_CHANNEL_ATTRIBUTE).get();
////        clientChannel.close();
//        clientChannel.writeAndFlush(msg, promise).addListener(ChannelFutureListener.CLOSE);
//    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
