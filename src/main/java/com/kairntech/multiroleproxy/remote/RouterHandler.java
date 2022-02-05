package com.kairntech.multiroleproxy.remote;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.remote.RouterHandler.RouteType.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class RouterHandler extends ChannelInboundHandlerAdapter {

    public enum RouteType {
        REGISTER_CLIENT,
        REGISTER_SPEC,
        PROXY
    }

    public static final String REGISTER_SPEC_URI = "/_register_spec";
    public static final String REGISTER_CLIENT_URI = "/_register_client";
    public static final AttributeKey<String> REMOTE_ADDRESS_ATTRIBUTE = AttributeKey.newInstance("remoteAddress");
    public static final AttributeKey<RouteType> ROUTE_TYPE_ATTRIBUTE = AttributeKey.newInstance("routeType");
    private static final Logger log = Logger.getLogger( RouterHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            Channel ch = ctx.channel();
            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "handling request: " + ctx.channel() + " " + msg);
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            if (remoteAddress instanceof InetSocketAddress) {
                String remoteIPv4 = getIpv4((InetSocketAddress) remoteAddress);
                ctx.channel().attr(REMOTE_ADDRESS_ATTRIBUTE).set(remoteIPv4);
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "request issuer IP is: " + remoteIPv4);
            } else {
                ctx.channel().attr(REMOTE_ADDRESS_ATTRIBUTE).set("");
                log.log(Level.WARNING, "unable to get request issuer IP");
            }
            HttpRequest request = (HttpRequest) msg;
//            send422ErrorAndCloseIfDecoderError(ctx, request);

            if (HttpUtil.is100ContinueExpected(request)) {
                // don't transmit to multiroles, will be more complex to handle
                request.headers().remove(HttpHeaderNames.EXPECT);
                send100Continue(ctx);
            }
            if (request.uri().equals(REGISTER_CLIENT_URI)) {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "'register client' request detected");
                ctx.channel().attr(ROUTE_TYPE_ATTRIBUTE).set(REGISTER_CLIENT);
            } else if (request.uri().equals(REGISTER_SPEC_URI)) {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "'register spec' request detected");
                ctx.channel().attr(ROUTE_TYPE_ATTRIBUTE).set(REGISTER_SPEC);
            } else {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "'proxy-able' request detected, removing pipeline reconfigurer and http object aggregator");
                ctx.channel().pipeline().remove(ReconfigureRemotePipelineHandler.class); // don't reorganize the pipeline
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
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "sending 100 continue :" + ctx.channel());
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER);
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
