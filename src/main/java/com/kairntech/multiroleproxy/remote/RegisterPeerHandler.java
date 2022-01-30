package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.Peers;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class RegisterPeerHandler extends SimpleChannelInboundHandler<Object> {

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Peers peers = ctx.channel().attr(Peers.PEERS_ATTRIBUTE).get();
        RouterHandler.RouteType routeType = ctx.channel().attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
        if (routeType == RouterHandler.RouteType.REGISTER_CLIENT && msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String body = request.content().toString(StandardCharsets.UTF_8);
            if (! writeAckResponse(request, ctx)) {
                // If keep-alive is off, close the connection once the content is fully written.
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private ArrayList<String> readLines(StringBuilder buf) {
        ArrayList<String> result = new ArrayList<>(5);
        try {
            BufferedReader reader = new BufferedReader(new StringReader(buf.toString()));
            String line;
            while ((line=reader.readLine()) != null) {
                result.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static void send422ErrorAndCloseIfDecoderError(ChannelHandlerContext ctx, HttpObject o) {

        DecoderResult result = o.decoderResult();
        if (!result.isSuccess()) {
            sendErrorAndClose(ctx, UNPROCESSABLE_ENTITY);
        }
    }

    private boolean writeAckResponse(HttpRequest request, ChannelHandlerContext ctx) {
        // Decide whether to close the connection or not.
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, request.decoderResult().isSuccess()? OK : BAD_REQUEST,
                Unpooled.EMPTY_BUFFER);
        // Add 'Content-Length' header only for a keep-alive connection.
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        if (keepAlive) {
            // Add keep alive header as per:
            // - https://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        // Write the response.
        ctx.writeAndFlush(response);

        return keepAlive;
    }

    private static void sendErrorAndClose(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}