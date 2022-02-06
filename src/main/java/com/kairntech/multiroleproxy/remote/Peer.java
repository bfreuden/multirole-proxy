package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.channel.Channel;

public class Peer {

    private final Sequencer sequencer;

    public Peer(Channel ch) {
        this.sequencer = new Sequencer(ch);
    }

    public Channel getChannel() {
        return sequencer.getUpstreamChannel();
    }

    public Sequencer getSequencer() {
        return this.sequencer;
    }
}
