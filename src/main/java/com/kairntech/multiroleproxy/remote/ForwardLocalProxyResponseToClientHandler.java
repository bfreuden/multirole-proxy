package com.kairntech.multiroleproxy.remote;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ForwardLocalProxyResponseToClientHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Channel> CLIENT_CHANNEL_ATTRIBUTE = AttributeKey.newInstance("clientChannel");

    private static final Logger log = Logger.getLogger( ForwardLocalProxyResponseToClientHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpObject) {
            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "sending data back to the client...: " + ctx.channel() + " " + msg);
            Channel clientChannel = ctx.channel().attr(CLIENT_CHANNEL_ATTRIBUTE).get();
            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "client channel is...: " + clientChannel);
            //TODO handle write error (with logging at least)
            if (msg instanceof LastHttpContent) {
                clientChannel.writeAndFlush(msg);
            } else {
                clientChannel.write(msg);
            }
        } else {
            log.log(Level.WARNING, "unsupported message: " + msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
