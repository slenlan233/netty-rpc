package com.xtwy.netty.factory;

import java.util.concurrent.TimeUnit;
import org.apache.curator.framework.*;
import org.apache.curator.retry.ExponentialBackoffRetry;

public final class ZookerFactory {
  private static CuratorFramework client;

  public static synchronized CuratorFramework create() {
    if (client != null) return client;
    CuratorFramework next =
        CuratorFrameworkFactory.builder()
            .connectString(System.getProperty("rpc.zk.address", "127.0.0.1:2181"))
            .sessionTimeoutMs(10000)
            .connectionTimeoutMs(5000)
            .retryPolicy(new ExponentialBackoffRetry(200, 3))
            .build();
    next.start();
    try {
      if (!next.blockUntilConnected(5, TimeUnit.SECONDS))
        throw new IllegalStateException("ZooKeeper connection timed out");
      try {
        next.create().creatingParentsIfNeeded().forPath("/netty");
      } catch (org.apache.zookeeper.KeeperException.NodeExistsException ignored) {
      }
      client = next;
      return client;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      next.close();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      next.close();
      throw new IllegalStateException("Cannot initialize ZooKeeper", e);
    }
  }

  public static synchronized void close() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  public static void main(String[] args) {
    try {
      create();
      System.out.println("ZooKeeper /netty ready");
    } finally {
      close();
    }
  }
}
