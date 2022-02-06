package com.kairntech.multiroleproxy.local;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpResponseDecoder;

public class ConnectionLostWatcherChannelInitializer extends ChannelInitializer {
    @Override
    protected void initChannel(Channel ch) {
        ch.pipeline().addLast(new HttpResponseDecoder());
    }
}
