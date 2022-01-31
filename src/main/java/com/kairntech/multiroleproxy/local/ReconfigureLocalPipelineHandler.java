package com.kairntech.multiroleproxy.local;

import io.netty.channel.*;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReconfigureLocalPipelineHandler extends SimpleChannelInboundHandler<Object> {

    private final Consumer<Channel> registrationCompleteCallback;
    private final EventLoopGroup group;
    boolean registrationSuccessful = false;

    private static final Logger log = Logger.getLogger( ReconfigureLocalPipelineHandler.class.getSimpleName().replace("Handler", "") );

    public ReconfigureLocalPipelineHandler(EventLoopGroup group, Consumer<Channel> registrationCompleteCallback) {
        this.registrationCompleteCallback = registrationCompleteCallback;
        this.group = group;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            if (response.status().equals(HttpResponseStatus.OK)) {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "local proxy registered into remote proxy: " + ctx.channel() + " " + msg);
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "waiting for next LastHttpContent to reconfigure pipeline : " + ctx.channel() + " " + msg);
                registrationSuccessful = true;
            } else {
                log.log(Level.SEVERE, "registration failed into remote proxy, closing channel: " + ctx.channel() + " " + msg);
                ctx.channel().close();
            }
        } else if (msg instanceof LastHttpContent) {
            if (registrationSuccessful) {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "reconfiguring pipeline as an http server + multirole forwarding: " + ctx.channel() + " " + msg);
                LocalProxyChannelInitializer.reconfigurePipeline(ctx.pipeline(), group);
                registrationCompleteCallback.accept(ctx.channel());
            } else {
                log.log(Level.SEVERE, "registration failed into remote proxy, closing channel: " + ctx.channel() + " " + msg);
                ctx.channel().close();
            }
        } else {
            log.log(Level.WARNING, "unexpected message type: " + ctx.channel() + " " + msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
