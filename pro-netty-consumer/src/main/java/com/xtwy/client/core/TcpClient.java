package com.xtwy.client.core;

import com.alibaba.fastjson.JSON;
import com.xtwy.client.handler.SimpleClientHandler;
import com.xtwy.client.param.*;
import com.xtwy.client.zk.ZookerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.*;
import io.netty.util.CharsetUtil;

public final class TcpClient {
  private static Bootstrap bootstrap;
  private static EventLoopGroup workers;
  private static ServerWatcher watcher;

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(TcpClient::close, "rpc-client-shutdown"));
  }

  private static synchronized void initialize() {
    if (bootstrap != null) return;
    workers = new NioEventLoopGroup(2);
    bootstrap =
        new Bootstrap()
            .group(workers)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .handler(
                new ChannelInitializer<SocketChannel>() {
                  protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(
                            new LineBasedFrameDecoder(1024 * 1024),
                            new StringDecoder(CharsetUtil.UTF_8),
                            new StringEncoder(CharsetUtil.UTF_8),
                            new SimpleClientHandler());
                  }
                });
    try {
      watcher = new ServerWatcher();
    } catch (Exception e) {
      close();
      throw new IllegalStateException("Cannot initialize RPC discovery", e);
    }
  }

  static ChannelFuture connect(String host, int port) {
    return bootstrap.connect(host, port);
  }

  public static Response send(ClientRequest request) {
    initialize();
    Channel channel = ChannelManager.get();
    // Register before sending: an immediate response must already have a waiting owner.
    DefaultFuture future = new DefaultFuture(request, channel);
    try {
      channel
          .writeAndFlush(JSON.toJSONString(request) + "\r\n")
          .addListener(
              (ChannelFutureListener)
                  sent -> {
                    if (!sent.isSuccess()) future.fail(sent.cause());
                  });
    } catch (RuntimeException e) {
      future.fail(e);
    }
    return future.get(Long.getLong("rpc.timeoutMillis", 3000L));
  }

  public static synchronized void close() {
    if (watcher != null) {
      try {
        watcher.close();
      } catch (Exception ignored) {
      }
      watcher = null;
    }
    ChannelManager.close();
    DefaultFuture.closeAll();
    ZookerFactory.close();
    if (workers != null) {
      workers.shutdownGracefully(0, 2, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
      workers = null;
    }
    bootstrap = null;
  }
}
