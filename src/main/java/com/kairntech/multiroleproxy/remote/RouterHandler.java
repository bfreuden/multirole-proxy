package com.kairntech.multiroleproxy.remote;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static com.kairntech.multiroleproxy.remote.RouterHandler.RouteType.PROXY;
import static com.kairntech.multiroleproxy.remote.RouterHandler.RouteType.REGISTER_CLIENT;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class RouterHandler extends ChannelInboundHandlerAdapter {

    public enum RouteType {
        REGISTER_CLIENT,
        PROXY
    }

    public static final String REGISTER_CLIENT_URI = "/_register_client";
    public static final AttributeKey<String> REMOTE_ADDRESS_ATTRIBUTE = AttributeKey.newInstance("remoteAddress");
    public static final AttributeKey<RouteType> ROUTE_TYPE_ATTRIBUTE = AttributeKey.newInstance("routeType");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            if (remoteAddress instanceof InetSocketAddress) {
                String remoteIPv4 = getIpv4((InetSocketAddress) remoteAddress);
                ctx.channel().attr(REMOTE_ADDRESS_ATTRIBUTE).set(remoteIPv4);
            } else {
                ctx.channel().attr(REMOTE_ADDRESS_ATTRIBUTE).set("");
            }
            HttpRequest request = (HttpRequest) msg;
//            send422ErrorAndCloseIfDecoderError(ctx, request);

            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }
            if (request.uri().equals(REGISTER_CLIENT_URI)) {
                ctx.channel().attr(ROUTE_TYPE_ATTRIBUTE).set(REGISTER_CLIENT);
            } else {
                ctx.channel().pipeline().remove(RemoteChannelSwitcherHandler.class); // don't reorganize the pipeline
                ctx.channel().pipeline().remove(HttpObjectAggregator.class); // handle chunks
                ctx.channel().attr(ROUTE_TYPE_ATTRIBUTE).set(PROXY);
            }
        }
        ctx.fireChannelRead(msg);
    }

    private String getIpv4(InetSocketAddress peerAddress) {
        InetAddress address = peerAddress.getAddress();
        StringBuilder ipv4b = new StringBuilder(16);
        for (byte b : ((Inet4Address) address).getAddress()) {
            ipv4b.append(Byte.toUnsignedInt(b));
            ipv4b.append('.');
        }
        ipv4b.setLength(ipv4b.length() - 1);
        return ipv4b.toString();
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER);
        ctx.write(response);
    }

}
