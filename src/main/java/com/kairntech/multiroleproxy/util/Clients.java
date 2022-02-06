package com.kairntech.multiroleproxy.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;

import java.util.HashMap;

public class Clients {

    private HashMap<String, Channel> clientsByUUID = new HashMap<>();

    public synchronized void clientConnected(String requestUUID, Channel channel) {
        clientsByUUID.put(requestUUID, channel);
        channel.closeFuture().addListener((ChannelFutureListener) future -> clientDisconnected(requestUUID));
    }

    public synchronized void clientDisconnected(String requestUuid) {
        clientsByUUID.remove(requestUuid);
    }

    public synchronized Channel getClientChannel(String requestUuid) {
        return clientsByUUID.get(requestUuid);
    }


}
