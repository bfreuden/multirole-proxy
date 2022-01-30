package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.Peers;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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

public class LocalProxyForwardHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Peers peers = ctx.channel().attr(Peers.PEERS_ATTRIBUTE).get();
        RouterHandler.RouteType routeType = ctx.channel().attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
        if (routeType == RouterHandler.RouteType.PROXY && msg instanceof HttpMessage) {
            peers.channel.writeAndFlush(msg);
        }

    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}