package com.kairntech.multiroleproxy.remote;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

// can't be a SimpleChannelInboundHandler because it will forward a message if not the target
public class OpenAPISpecHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(OpenAPISpecHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Peers peers = ctx.channel().attr(Peers.PEERS_ATTRIBUTE).get();
        RouterHandler.RouteType routeType = ctx.channel().attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
        if (routeType == RouterHandler.RouteType.OPENAPI) {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                if (!request.decoderResult().isSuccess()) {
                    writeResponse(ctx, BAD_REQUEST, "http decoder error");
                } else {
                    ByteBuf spec = peers.getSpec(ctx.channel().alloc());
                    writeResponse(ctx, OK, spec, HttpHeaderValues.APPLICATION_JSON);
                }
            } else {
                log.log(Level.WARNING, "unsupported message: " + msg);
            }
            ReferenceCountUtil.release(msg);
        } else {
            // forward message to the next
            ctx.fireChannelRead(msg);
        }
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer(message, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN + ";charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

    }
    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status, ByteBuf message, AsciiString contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, message);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }
}