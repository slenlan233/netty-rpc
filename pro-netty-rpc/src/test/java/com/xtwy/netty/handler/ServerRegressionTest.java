package com.xtwy.netty.handler;

import static org.junit.Assert.*;

import com.alibaba.fastjson.JSON;
import com.xtwy.medium.Media;
import com.xtwy.netty.handler.param.ServerRequest;
import com.xtwy.rpc.Response;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

public class ServerRegressionTest {
  @Test
  public void pongIsConsumed() {
    EmbeddedChannel channel = new EmbeddedChannel(new ServerHandler());
    assertFalse(channel.writeInbound("pong"));
    assertNull(channel.readOutbound());
    channel.finishAndReleaseAll();
  }

  @Test
  public void unknownCommandPreservesRequestId() {
    ServerRequest request = new ServerRequest();
    request.setId(77L);
    request.setCommand("missing");
    Response response = Media.newInstance().process(request);
    assertEquals(Long.valueOf(77), response.getId());
    assertEquals("NO_METHOD", response.getCode());
  }

  @Test
  public void malformedInputReturnsFailure() {
    EmbeddedChannel channel = new EmbeddedChannel(new ServerHandler());
    channel.writeInbound("bad-json");
    String message = channel.readOutbound();
    Response response = JSON.parseObject(message, Response.class);
    assertEquals("BAD_REQUEST", response.getCode());
    channel.finishAndReleaseAll();
  }
}
