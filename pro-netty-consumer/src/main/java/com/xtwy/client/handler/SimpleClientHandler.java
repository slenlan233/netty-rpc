package com.xtwy.client.handler;

import com.alibaba.fastjson.JSON;
import com.xtwy.client.core.DefaultFuture;
import com.xtwy.client.param.Response;
import io.netty.channel.*;

public class SimpleClientHandler extends SimpleChannelInboundHandler<String> {
  protected void channelRead0(ChannelHandlerContext ctx, String message) {
    if ("ping".equals(message)) {
      ctx.writeAndFlush("pong\r\n");
      return;
    }
    DefaultFuture.receive(JSON.parseObject(message, Response.class));
  }

  public void channelInactive(ChannelHandlerContext ctx) {
    DefaultFuture.disconnected(ctx.channel());
    ctx.fireChannelInactive();
  }

  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ctx.close();
  }
}
