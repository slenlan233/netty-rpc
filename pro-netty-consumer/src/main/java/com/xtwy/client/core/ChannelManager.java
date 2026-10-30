package com.xtwy.client.core;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ChannelManager {
  private static final AtomicLong POSITION = new AtomicLong();
  private static List<String> servers = Collections.emptyList();
  private static final Map<String, Channel> CHANNELS = new HashMap<>();

  public static synchronized void updateServers(List<String> nodes) {
    List<String> next = new ArrayList<>();
    for (String node : nodes) {
      String[] p = node.split("#");
      if (p.length < 2) continue;
      try {
        int port = Integer.parseInt(p[1]);
        int weight = p.length > 2 ? Integer.parseInt(p[2]) : 1;
        if (port < 1 || port > 65535 || weight < 0 || weight > 1000)
          throw new IllegalArgumentException("Invalid server: " + node);
        // Weight repeats selection slots, not physical TCP connections.
        for (int i = 0; i < weight; i++) next.add(p[0] + "#" + port);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid server: " + node, e);
      }
    }
    servers = Collections.unmodifiableList(next);
    Iterator<Map.Entry<String, Channel>> it = CHANNELS.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Channel> e = it.next();
      if (!next.contains(e.getKey())) {
        e.getValue().close();
        it.remove();
      }
    }
  }

  public static synchronized Channel get() {
    if (servers.isEmpty()) throw new IllegalStateException("No RPC servers available");
    int start = (int) Math.floorMod(POSITION.getAndIncrement(), (long) servers.size());
    Set<String> attempted = new HashSet<>();
    Throwable failure = null;
    for (int i = 0; i < servers.size(); i++) {
      String address = servers.get((start + i) % servers.size());
      if (!attempted.add(address)) continue;
      Channel channel = CHANNELS.get(address);
      if (channel != null && channel.isActive()) return channel;
      String[] p = address.split("#");
      try {
        ChannelFuture future = TcpClient.connect(p[0], Integer.parseInt(p[1]));
        future.sync();
        channel = future.channel();
        CHANNELS.put(address, channel);
        return channel;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Connection interrupted", e);
      } catch (Exception e) {
        failure = e;
      }
    }
    throw new IllegalStateException("Cannot connect to any RPC server", failure);
  }

  public static synchronized void close() {
    for (Channel c : CHANNELS.values()) c.close();
    CHANNELS.clear();
    servers = Collections.emptyList();
  }
}
