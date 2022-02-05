package com.kairntech.multiroleproxy.local;

import io.netty.channel.EventLoopGroup;

import java.util.*;
import java.util.logging.Logger;

public class Multiroles {

    private static final Logger log = Logger.getLogger(Multiroles.class.getSimpleName());

    private final EventLoopGroup group;
    private final HashSet<Multirole> servers = new HashSet<>();
    private final MultiroleChangeNotifier multiroleChangeNotifier;

    public Multiroles(EventLoopGroup group, MultiroleChangeNotifier multiroleChangeNotifier) {
        this.group = group;
        this.multiroleChangeNotifier = multiroleChangeNotifier;
    }

    public synchronized void addServer(String host, Integer port) {
        log.info("adding multirole server: " + host+ ":" + port);
        Multirole multirole = new Multirole(group, multiroleChangeNotifier, host, port);
        if (servers.contains(multirole))
            throw new IllegalArgumentException("multirole already exists");
        servers.add(multirole);
        log.info("multirole server added: " + host+ ":" + port);
        multirole.connect();
    }

    public synchronized void deleteServer(String host, Integer port) {
        log.info("deleting multirole server: " + host+ ":" + port);
        Multirole multirole = new Multirole(group, multiroleChangeNotifier, host, port);
        if (!servers.contains(multirole))
            throw new NoSuchElementException("multirole does not exists");
        log.info("multirole server deleted: " + host+ ":" + port);
        servers.remove(multirole);
    }

    public synchronized HashSet<Multirole> getServers() {
        return new HashSet<>(this.servers);
    }

}
