package com.kairntech.multiroleproxy.local;

import io.netty.channel.EventLoopGroup;

import java.util.*;
import java.util.logging.Logger;

public class Multiroles {

    private static final Logger log = Logger.getLogger(Multiroles.class.getSimpleName());

    private final EventLoopGroup group;
    private final HashMap<Multirole, Multirole> servers = new HashMap<>();
    private final MultiroleChangeNotifier multiroleChangeNotifier;

    public Multiroles(EventLoopGroup group, MultiroleChangeNotifier multiroleChangeNotifier) {
        this.group = group;
        this.multiroleChangeNotifier = multiroleChangeNotifier;
    }

    public synchronized void addServer(String host, Integer port) {
        log.info("adding multirole server: " + host+ ":" + port);
        Multirole multirole = new Multirole(group, multiroleChangeNotifier, host, port);
        if (servers.containsKey(multirole))
            throw new IllegalArgumentException("multirole already exists");
        servers.put(multirole, multirole);
        log.info("multirole server added: " + host+ ":" + port);
        multirole.connect();
    }

    public synchronized void deleteServer(String host, Integer port) {
        log.info("deleting multirole server: " + host+ ":" + port);
        Multirole multirole = servers.get(new Multirole(group, multiroleChangeNotifier, host, port));
        if (multirole == null)
            throw new NoSuchElementException("multirole does not exists");
        log.info("multirole server deleted: " + host+ ":" + port);
        multirole.notifyRemoved();
        servers.remove(multirole);
    }

    public synchronized HashSet<Multirole> getServers() {
        return new HashSet<>(this.servers.keySet());
    }

}
