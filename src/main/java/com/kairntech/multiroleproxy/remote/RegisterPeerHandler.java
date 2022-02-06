package com.kairntech.multiroleproxy.remote;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.local.MultiroleChangeNotifier.X_LOCAL_PROXY_ID_HEADER;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

// can't be a SimpleChannelInboundHandler because it will forward a message if not the target
public class RegisterPeerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger( RegisterPeerHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        RouterHandler.RouteType routeType = ctx.channel().attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
        if (routeType == RouterHandler.RouteType.REGISTER_CLIENT) {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                if (!request.decoderResult().isSuccess())  {
                    writeResponse(ctx, BAD_REQUEST, "http decoder error", true);
                } else {
                    String proxyId = request.headers().get(X_LOCAL_PROXY_ID_HEADER);
                    if (proxyId == null) {
                        writeResponse(ctx, BAD_REQUEST, "missing header: " + X_LOCAL_PROXY_ID_HEADER, true);
                    } else {
                        ctx.channel().attr(Peers.PEERS_ID_ATTRIBUTE).set(proxyId);
                        writeResponse(ctx, OK, "peer registered", false);
                    }
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

    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message, boolean close) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer(message, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN + ";charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, close ? HttpHeaderValues.CLOSE : HttpHeaderValues.KEEP_ALIVE);
        ChannelFuture channelFuture = ctx.writeAndFlush(response);
        if (close)
            channelFuture.addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}