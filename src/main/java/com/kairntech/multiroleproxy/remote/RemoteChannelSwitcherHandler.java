package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.Peers;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpRequestEncoder;

public class RemoteChannelSwitcherHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise).addListener(e -> {
            Peers peers = ctx.channel().attr(Peers.PEERS_ATTRIBUTE).get();
            peers.channel = ctx.channel();
            reconfigurePipeline(ctx.channel().pipeline());
        });
    }

    private void reconfigurePipeline(ChannelPipeline p) {
        while (p.last() != null)
            p.removeLast();

        p.addLast(new HttpClientCodec());
//        p.addLast(new HttpRequestEncoder());
        // Remove the following line if you don't want automatic content decompression.
        p.addLast(new HttpContentDecompressor());
        // Uncomment the following line if you don't want to handle HttpContents.
        //p.addLast(new HttpObjectAggregator(1048576));

//        p.addLast(new ResponseForwardHandler());
    }

}
