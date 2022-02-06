package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ForwardClientRequestToLocalProxyHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger( ForwardClientRequestToLocalProxyHandler.class.getSimpleName().replace("Handler", "") );
    private Sequencer sequencer;
    private Sequencer.ChannelHandlers handlers;

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        RouterHandler.RouteType routeType = ctx.channel().attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
        if (routeType == RouterHandler.RouteType.PROXY) {
            if (msg instanceof HttpObject) {
                HttpObject message = (HttpObject) msg;
                if (msg instanceof HttpRequest) {
                    HttpRequest request = (HttpRequest) msg;
                    // find target peer
                    Peers peers = ctx.channel().attr(Peers.PEERS_ATTRIBUTE).get();
                    Peer peer = peers.getPeer(request.uri());
                    if (peer != null) {
                        sequencer = peer.getSequencer();
                        handlers = new Sequencer.ChannelHandlers(ctx.channel(), () -> write503Response(ctx, "connection lost with local proxy"), null);
                    }
                }
                if (sequencer != null) {
                    maybeLogFinest(log, () -> "sending client data to local proxy...: " + ctx.channel() + " " + msg);
                    maybeLogFinest(log, () -> "... local proxy channel is: " + sequencer.getUpstreamChannel());
                    sequencer.write(handlers, message);
                } else {
                    log.log(Level.WARNING, "no local proxy registered for this request, can't send data: " + ctx.channel() + " " + msg);
                    ReferenceCountUtil.release(msg);
                }
                if (msg instanceof LastHttpContent) {
                    if (sequencer == null)
                        write503Response(ctx, "no peer registered");
                    sequencer = null;
                    handlers = null;
                }
            } else {
                ReferenceCountUtil.release(msg);
                log.log(Level.WARNING, "unexpected message type: " + ctx.channel() + " " + msg);
            }
        } else {
            ReferenceCountUtil.release(msg);
            maybeLogFinest(log, () -> "ignoring message not targeting this handler: " + ctx.channel() + " " + msg);
        }
    }

    private void write503Response(ChannelHandlerContext ctx, String message) {
        maybeLogFinest(log, () -> "sending 503 response to client: " + ctx.channel());
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(503, message),
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