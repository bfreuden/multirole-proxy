package com.kairntech.multiroleproxy.remote;

import io.netty.channel.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

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
                String peerId = ctx.channel().attr(Peers.PEERS_ID_ATTRIBUTE).get();
                peers.peerConnected(peerId, channel);
                maybeLogFinest(log, () -> "reconfiguring pipeline as http client + forwarding proxy: " + channel + " " + msg);
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
