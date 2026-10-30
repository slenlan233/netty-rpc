package com.xtwy.netty.init;

import com.xtwy.medium.Media;
import com.xtwy.netty.factory.ZookerFactory;
import com.xtwy.netty.handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.apache.curator.framework.recipes.nodes.PersistentNode;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class NettyInitial implements ApplicationListener<ContextRefreshedEvent>, DisposableBean {
  private EventLoopGroup acceptors, workers;
  private DefaultEventExecutorGroup business;
  private Channel server;
  private PersistentNode registration;

  public synchronized void start() {
    if (server != null) return;
    acceptors = new NioEventLoopGroup(1);
    workers = new NioEventLoopGroup(2);
    business = new DefaultEventExecutorGroup(4);
    try {
      ServerBootstrap bootstrap =
          new ServerBootstrap()
              .group(acceptors, workers)
              .channel(NioServerSocketChannel.class)
              .option(ChannelOption.SO_BACKLOG, 128)
              .childOption(ChannelOption.SO_KEEPALIVE, true)
              .childHandler(
                  new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel ch) {
                      ch.pipeline()
                          .addLast(
                              new LineBasedFrameDecoder(1024 * 1024),
                              new StringDecoder(CharsetUtil.UTF_8),
                              new StringEncoder(CharsetUtil.UTF_8),
                              new IdleStateHandler(60, 0, 20));
                      ch.pipeline().addLast(business, "rpc-business", new ServerHandler());
                    }
                  });
      server =
          bootstrap
              .bind(
                  System.getProperty("rpc.bind.host", "0.0.0.0"),
                  Integer.getInteger("rpc.server.port", 8888))
              .sync()
              .channel();
      {
        String prefix =
            "/netty/"
                + System.getProperty("rpc.server.host", "127.0.0.1")
                + "#"
                + getPort()
                + "#"
                + Integer.getInteger("rpc.server.weight", 2)
                + "#";
        registration =
            new PersistentNode(
                ZookerFactory.create(),
                CreateMode.EPHEMERAL_SEQUENTIAL,
                false,
                prefix,
                new byte[0]);
        registration.start();
        if (!registration.waitForInitialCreate(10, TimeUnit.SECONDS))
          throw new IllegalStateException("Service registration timed out");
      }
      System.out.println("RPC server ready on port " + getPort());
    } catch (Exception e) {
      destroy();
      throw new IllegalStateException("Cannot start RPC server", e);
    }
  }

  public int getPort() {
    return ((InetSocketAddress) server.localAddress()).getPort();
  }

  public void onApplicationEvent(ContextRefreshedEvent event) {
    if (event.getApplicationContext().getParent() == null) start();
  }

  public synchronized void destroy() {
    if (registration != null) {
      try {
        registration.close();
      } catch (Exception ignored) {
      }
      registration = null;
    }
    if (server != null) {
      server.close().syncUninterruptibly();
      server = null;
    }
    if (workers != null) {
      workers.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
      workers = null;
    }
    if (acceptors != null) {
      acceptors.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
      acceptors = null;
    }
    if (business != null) {
      business.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
      business = null;
    }
    ZookerFactory.close();
    Media.beanMap.clear();
  }
}
