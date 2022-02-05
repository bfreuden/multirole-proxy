package com.kairntech.multiroleproxy.util;


import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

class SimpleHttpServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = Logger.getLogger( SimpleHttpServerHandler.class.getSimpleName().replace("Handler", "") );
    private final SimpleHttpServer server;



    public SimpleHttpServerHandler(SimpleHttpServer server) {
        this.server = server;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }
            if (!request.decoderResult().isSuccess()) {
                writeEmptyResponse(ctx, BAD_REQUEST);
            } else {
                QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
                String uri = queryStringDecoder.uri();
                Function<SimpleHttpRequest, FullHttpResponse> handler = server.matchingHandler(uri, request.method());
                if (handler == null) {
                    writeEmptyResponse(ctx, NOT_FOUND);
                } else {
                    try {
                        FullHttpResponse response = handler.apply(new SimpleHttpRequest(request, queryStringDecoder));
                        writeResponse(ctx, response);
                    } catch (Throwable t) {
                        writeEmptyResponse(ctx, INTERNAL_SERVER_ERROR);
                    }
                }
            }
        } else {
            log.log(Level.WARNING, "unexpected message: " + msg);
        }

    }

    private void writeEmptyResponse(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

    }
    private void writeResponse(ChannelHandlerContext ctx, FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }
}