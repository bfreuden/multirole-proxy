package com.kairntech.multiroleproxy.remote;

import io.netty.channel.*;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ReconfigureRemotePipelineHandler extends ChannelOutboundHandlerAdapter {

    private static final Logger log = Logger.getLogger( ReconfigureRemotePipelineHandler.class.getSimpleName().replace("Handler", "") );
    boolean reconfigurationDone = false;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ChannelFuture writeFuture = ctx.write(msg, promise);
        Channel channel = ctx.channel();
        if (!reconfigurationDone) {
            reconfigurationDone = true;
            writeFuture.addListener(e -> {
                Peers peers = channel.attr(Peers.PEERS_ATTRIBUTE).get();
                peers.peerConnected(channel);
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "reconfiguring pipeline as http client + forwarding proxy: " + channel + " " + msg);
                RemoteProxyChannelInitializer.reconfigurePipeline(channel.pipeline());
            });
        } else {
            log.log(Level.SEVERE, "reconfiguration done before the last message: " + channel + " " + msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
