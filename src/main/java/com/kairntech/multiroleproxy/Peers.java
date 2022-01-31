package com.kairntech.multiroleproxy;

import io.netty.channel.Channel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeKey;

public class Peers {

    public static final AttributeKey<Peers> PEERS_ATTRIBUTE = AttributeKey.newInstance("peers");
    private Channel channel;

    public Channel getPeerChannel() {
        return this.channel;
    }

    public void peerConnected(Channel ch) {
        this.channel = ch;
    }

    public void peerDisconnected(Channel ch) {
        this.channel = null;
    }
}
