package com.kairntech.multiroleproxy.local;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ForwardMultiroleResponseToRemoteHandler extends ChannelInboundHandlerAdapter {

    private final Supplier<Channel>  replyChannel;

    private static final Logger log = Logger.getLogger( ForwardMultiroleResponseToRemoteHandler.class.getSimpleName().replace("Handler", "") );

    public ForwardMultiroleResponseToRemoteHandler(Supplier<Channel> replyChannel) {
        this.replyChannel = replyChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpObject) {
            System.out.println("writing data back to remote: " + ctx.channel() + " " + msg);
            Channel channel = replyChannel.get();
            if (msg instanceof LastHttpContent) {
                channel.writeAndFlush(msg);
            } else {
                channel.write(msg);
            }
        } else {
            log.log(Level.WARNING, "unsupported message: " + msg);
            // FIXME release msg!!
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
