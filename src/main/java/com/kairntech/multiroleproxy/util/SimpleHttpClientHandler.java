package com.kairntech.multiroleproxy.util;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.util.SimpleHttpClient.HANDLER_ATTR;

class SimpleHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private Consumer<AsyncResult<FullHttpResponse>> asyncResultConsumer;

    private static final Logger log = Logger.getLogger( SimpleHttpClientHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        asyncResultConsumer = ctx.channel().attr(HANDLER_ATTR).get();
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            asyncResultConsumer.accept(new AsyncResult<>(response));
        } else if (msg instanceof LastHttpContent) {
            ctx.close();
        } else {
            log.log(Level.WARNING, "unexpected message: " + msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "exception caught: " + ctx.channel(), cause);
        if (asyncResultConsumer != null)
            asyncResultConsumer.accept(new AsyncResult<>(cause));
        ctx.close();
    }
}