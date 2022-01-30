package com.kairntech.multiroleproxy.local;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContext;

import java.util.function.Supplier;

public class MultiroleClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final Channel replyChannel;

    public MultiroleClientChannelInitializer(Supplier<Channel> replyChannel, SslContext sslCtx) {
        this.sslCtx = sslCtx;
        this.replyChannel = replyChannel.get();
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        // Enable HTTPS if necessary.
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }

        p.addLast(new HttpClientCodec());

        // Remove the following line if you don't want automatic content decompression.
//        p.addLast(new HttpContentDecompressor());

        // Uncomment the following line if you don't want to handle HttpContents.
        //p.addLast(new HttpObjectAggregator(1048576));

    }
}