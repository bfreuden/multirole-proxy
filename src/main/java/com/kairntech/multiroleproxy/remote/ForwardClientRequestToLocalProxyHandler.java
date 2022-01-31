package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.Peers;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.remote.ForwardLocalProxyResponseToClientHandler.CLIENT_CHANNEL_ATTRIBUTE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ForwardClientRequestToLocalProxyHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger( ForwardClientRequestToLocalProxyHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Peers peers = ctx.channel().attr(Peers.PEERS_ATTRIBUTE).get();
        RouterHandler.RouteType routeType = ctx.channel().attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
        if (routeType == RouterHandler.RouteType.PROXY ) {
            if (msg instanceof HttpObject) {
                Channel peerChannel = peers.getPeerChannel();
                if (peerChannel != null) {
                    if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "sending client data to local proxy...: " + ctx.channel() + " " + msg);
                    if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "... local proxy channel is: " + peerChannel);
                    peerChannel.attr(CLIENT_CHANNEL_ATTRIBUTE).set(ctx.channel());
                    peerChannel.writeAndFlush(msg);
                } else {
                    log.log(Level.WARNING, "no local proxy registered, can't to send data: " + ctx.channel() + " " + msg);
                    ReferenceCountUtil.release(msg);
                    write503Response(ctx);
                }
            } else {
                ReferenceCountUtil.release(msg);
                log.log(Level.WARNING, "unexpected message type: " + ctx.channel() + " " + msg);
            }
        } else {
            ReferenceCountUtil.release(msg);
            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "ignoring message not targeting this handler: " + ctx.channel() + " " + msg);
        }
    }

    private void write503Response(ChannelHandlerContext ctx) {
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "sending 503 response to client: " + ctx.channel());
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(503, "no peer registered"),
                Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }
}