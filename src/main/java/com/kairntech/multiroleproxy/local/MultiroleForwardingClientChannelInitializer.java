package com.kairntech.multiroleproxy.local;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContext;

import java.util.function.Supplier;

public class MultiroleForwardingClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final Supplier<Channel> replyChannel;

    public MultiroleForwardingClientChannelInitializer(Supplier<Channel> replyChannel, SslContext sslCtx) {
        this.sslCtx = sslCtx;
        this.replyChannel = replyChannel;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        // Enable HTTPS if necessary.
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }
        ResponseTweakerHandler responseConnectionTweaker = new ResponseTweakerHandler();
        ResponseTweakerHandler.RequestTweakerHandler requestConnectionTweaker = responseConnectionTweaker.requestConnectionTweaker;

        p.addLast(new HttpClientCodec());
        p.addLast(responseConnectionTweaker);
        p.addLast(requestConnectionTweaker);
        p.addLast(new ForwardMultiroleResponseToRemoteHandler(replyChannel));

//        p.addLast(new HttpRequestEncoder());

        // Remove the following line if you don't want automatic content decompression.
//        p.addLast(new HttpContentDecompressor());

        // Uncomment the following line if you don't want to handle HttpContents.
        //p.addLast(new HttpObjectAggregator(1048576));

    }
}