package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.Clients;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.remote.Peers.CLIENTS_ATTRIBUTE;
import static com.kairntech.multiroleproxy.remote.RouterHandler.X_REQUEST_UUID_HEADER;
import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

public class ForwardLocalProxyResponseToClientHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(ForwardLocalProxyResponseToClientHandler.class.getSimpleName().replace("Handler", ""));

    private Channel clientChannel;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Clients clients = ctx.channel().attr(CLIENTS_ATTRIBUTE).get();
        if (msg instanceof HttpObject) {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse)msg;
                String requestUUID = response.headers().get(X_REQUEST_UUID_HEADER);
                if (requestUUID != null)
                    clientChannel = clients.getClientChannel(requestUUID);
            }
            if (clientChannel == null || !clientChannel.isActive()) {
                if (clientChannel == null)
                    log.log(Level.WARNING, "can't find client channel, discarding message: " + msg);
                else
                    log.log(Level.WARNING, "inactive client channel, discarding message: " + msg);
                ReferenceCountUtil.release(msg);
            } else {
                maybeLogFinest(log, () -> "sending data back to the client...: " + ctx.channel() + " " + msg);
                maybeLogFinest(log, () -> "client channel is...: " + clientChannel);
                //TODO handle write error (with logging at least)
                if (msg instanceof LastHttpContent) {
                    clientChannel.writeAndFlush(msg);
                    clientChannel = null;
                } else {
                    clientChannel.write(msg);
                }
            }
        } else {
            ReferenceCountUtil.release(msg);
            log.log(Level.WARNING, "unsupported message: " + msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
