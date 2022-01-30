package com.kairntech.multiroleproxy.remote.attic;

import com.kairntech.multiroleproxy.remote.ResponseForwardHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;

public class ResponseLengthCounterHandler extends ChannelOutboundHandlerAdapter {

    public static final AttributeKey<Integer> RESPONSE_LENGTH = AttributeKey.newInstance("responseLength");

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof DefaultFullHttpResponse) {
            int len = ((DefaultFullHttpResponse) msg).content().writableBytes();
            ctx.channel().attr(RESPONSE_LENGTH).set(len);
            ctx.write(msg, promise);
        } else {
            ctx.channel().attr(RESPONSE_LENGTH).set(null);
        }
    }


    private void reconfigurePipeline(ChannelPipeline p) {
        while (p.last() != null)
            p.removeLast();

        p.addLast(new HttpClientCodec());
        // Remove the following line if you don't want automatic content decompression.
        p.addLast(new HttpContentDecompressor());
        // Uncomment the following line if you don't want to handle HttpContents.
        //p.addLast(new HttpObjectAggregator(1048576));

        p.addLast(new ResponseForwardHandler());
    }

}
