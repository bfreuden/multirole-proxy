package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.NoSuchElementException;

public class Peers {

    public static final AttributeKey<Peers> PEERS_ATTRIBUTE = AttributeKey.newInstance("peers");
    public static final AttributeKey<String> PEERS_ID_ATTRIBUTE = AttributeKey.newInstance("peerId");
    private Channel channel;

    public Channel getPeerChannel() {
        return this.channel;
    }

    public void peerConnected(String peerId, Channel ch) {
        this.channel = ch;
    }

    public void peerDisconnected(Channel ch) {
        this.channel = null;
    }

    public void declareSpec(String peerId, String multiroleId, String specMd5, OpenAPISpecParser.OpenAPISpec spec) {

    }

    public ByteBuf getSpec() {
        throw new NoSuchElementException();
    }
}
