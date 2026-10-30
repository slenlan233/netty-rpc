package com.xtwy.netty.handler;

import com.alibaba.fastjson.JSON;
import com.xtwy.medium.Media;
import com.xtwy.netty.handler.param.ServerRequest;
import com.xtwy.rpc.Response;
import io.netty.channel.*;
import io.netty.handler.timeout.*;

public class ServerHandler extends SimpleChannelInboundHandler<String> {
  protected void channelRead0(ChannelHandlerContext ctx, String message) {
    if ("pong".equals(message)) return;
    if ("ping".equals(message)) {
      ctx.writeAndFlush("pong\r\n");
      return;
    }
    Response response;
    try {
      ServerRequest request = JSON.parseObject(message, ServerRequest.class);
      response = Media.newInstance().process(request);
    } catch (Exception e) {
      response = Response.failure(null, "BAD_REQUEST", "Invalid request JSON");
    }
    ctx.writeAndFlush(JSON.toJSONString(response) + "\r\n");
  }

  public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
    if (event instanceof IdleStateEvent) {
      IdleState state = ((IdleStateEvent) event).state();
      if (state == IdleState.READER_IDLE) ctx.close();
      else if (state == IdleState.ALL_IDLE) ctx.writeAndFlush("ping\r\n");
    } else ctx.fireUserEventTriggered(event);
  }

  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ctx.close();
  }
}
