package com.kairntech.multiroleproxy;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class Peers {

    public static final AttributeKey<Peers> PEERS_ATTRIBUTE = AttributeKey.newInstance("peers");
    public Channel channel;
}
