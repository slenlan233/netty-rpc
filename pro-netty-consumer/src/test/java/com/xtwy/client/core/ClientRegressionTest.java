package com.xtwy.client.core;

import static org.junit.Assert.*;

import com.xtwy.client.handler.SimpleClientHandler;
import com.xtwy.client.param.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import org.junit.*;

public class ClientRegressionTest {
  @After
  public void cleanup() {
    ChannelManager.close();
    DefaultFuture.closeAll();
  }

  @Test
  public void requestIdsAreUnique() {
    Set<Long> ids = new HashSet<>();
    for (int i = 0; i < 10000; i++) assertTrue(ids.add(new ClientRequest().getId()));
  }

  @Test
  public void earlyResponseIsKeptAndCleaned() {
    ClientRequest q = new ClientRequest();
    DefaultFuture f = new DefaultFuture(q, null);
    Response r = new Response();
    r.setId(q.getId());
    DefaultFuture.receive(r);
    assertSame(r, f.get(100));
    assertEquals(0, DefaultFuture.pendingCount());
  }

  @Test
  public void independentRequestsCanCompleteOutOfOrder() {
    ClientRequest a = new ClientRequest(), b = new ClientRequest();
    DefaultFuture fa = new DefaultFuture(a, null), fb = new DefaultFuture(b, null);
    Response rb = new Response();
    rb.setId(b.getId());
    DefaultFuture.receive(rb);
    Response ra = new Response();
    ra.setId(a.getId());
    DefaultFuture.receive(ra);
    assertSame(ra, fa.get(100));
    assertSame(rb, fb.get(100));
    assertEquals(0, DefaultFuture.pendingCount());
  }

  @Test
  public void timeoutCleansPendingRecord() {
    DefaultFuture f = new DefaultFuture(new ClientRequest(), null);
    try {
      f.get(20);
      fail("Expected timeout");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("timed out"));
    }
    assertEquals(0, DefaultFuture.pendingCount());
  }

  @Test
  public void disconnectEndsWaitImmediately() {
    EmbeddedChannel channel = new EmbeddedChannel();
    DefaultFuture f = new DefaultFuture(new ClientRequest(), channel);
    DefaultFuture.disconnected(channel);
    try {
      f.get(1000);
      fail("Expected disconnect");
    } catch (IllegalStateException expected) {
    }
    assertEquals(0, DefaultFuture.pendingCount());
    channel.finishAndReleaseAll();
  }

  @Test
  public void emptyRegistryFailsClearly() {
    ChannelManager.updateServers(Collections.emptyList());
    try {
      ChannelManager.get();
      fail("Expected no servers");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("No RPC servers"));
    }
  }

  @Test
  public void clientAnswersHeartbeat() {
    EmbeddedChannel channel = new EmbeddedChannel(new SimpleClientHandler());
    channel.writeInbound("ping");
    assertEquals("pong\r\n", channel.readOutbound());
    channel.finishAndReleaseAll();
  }
}
