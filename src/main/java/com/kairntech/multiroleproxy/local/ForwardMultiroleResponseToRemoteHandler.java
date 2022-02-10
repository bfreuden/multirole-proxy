package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.local.Multiroles.MULTIROLE_ATTRIBUTE;
import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

public class ForwardMultiroleResponseToRemoteHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger( ForwardMultiroleResponseToRemoteHandler.class.getSimpleName().replace("Handler", "") );
    private boolean upstreamClosed = false;
    private Sequencer.ChannelHandlers tuple;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (upstreamClosed) {
            maybeLogFinest(log, () -> "remote proxy channel closed, discarding the message: " + ctx.channel() + " " + msg);
            ReferenceCountUtil.release(msg);
        } else {
            if (msg instanceof HttpObject) {
                if (msg instanceof HttpResponse)
                    tuple = new Sequencer.ChannelHandlers(ctx.channel(), () -> upstreamClosed = true, ctx::close);
                HttpObject message = (HttpObject) msg;
                Multirole multirole = ctx.channel().attr(MULTIROLE_ATTRIBUTE).get();
                maybeLogFinest(log, () -> "writing data back to remote: " + ctx.channel() + " " + msg);
                Sequencer sequencer = multirole.getSequencer();
                sequencer.write(tuple, message);
            } else {
                log.log(Level.WARNING, "unsupported message: " + msg);
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
