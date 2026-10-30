package com.xtwy.integration;

import static org.junit.Assert.*;

import com.xtwy.annotation.Remote;
import com.xtwy.client.annotation.RemoteInvoke;
import com.xtwy.client.core.*;
import com.xtwy.client.param.ClientRequest;
import com.xtwy.client.proxy.InvokeProxy;
import com.xtwy.medium.InitialMedium;
import com.xtwy.netty.init.NettyInitial;
import com.xtwy.rpc.Response;
import com.xtwy.user.model.User;
import com.xtwy.user.remote.*;
import com.xtwy.user.service.UserService;
import java.util.*;
import java.util.concurrent.*;
import org.apache.curator.test.TestingServer;
import org.junit.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class RpcIntegrationTest {
  private TestingServer zk;
  private AnnotationConfigApplicationContext context;
  private Caller caller;

  public interface ExtraRemote {
    String hello();

    String join(String a, int b);

    String echo(String value);

    String echo(int value);

    String fail();

    String slow(long millis);
  }

  @Remote
  public static class ExtraService implements ExtraRemote {
    public String hello() {
      return "hello";
    }

    public String join(String a, int b) {
      return a + b;
    }

    public String echo(String value) {
      return "string:" + value;
    }

    public String echo(int value) {
      return "int:" + value;
    }

    public String fail() {
      throw new IllegalArgumentException("expected failure");
    }

    public String slow(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return "done";
    }
  }

  public static class ParentCaller {
    @RemoteInvoke protected ExtraRemote extra;
  }

  public static class Caller extends ParentCaller {
    @RemoteInvoke UserRemote users;
  }

  @Before
  public void start() throws Exception {
    zk = new TestingServer();
    System.setProperty("rpc.zk.address", zk.getConnectString());
    System.setProperty("rpc.server.port", "0");
    System.setProperty("rpc.timeoutMillis", "3000");
    context = new AnnotationConfigApplicationContext();
    context.register(
        InitialMedium.class,
        InvokeProxy.class,
        NettyInitial.class,
        UserService.class,
        UserRemoteImpl.class,
        ExtraService.class,
        Caller.class);
    context.refresh();
    caller = context.getBean(Caller.class);
  }

  @After
  public void stop() throws Exception {
    TcpClient.close();
    if (context != null) context.close();
    if (zk != null) zk.close();
    System.clearProperty("rpc.zk.address");
    System.clearProperty("rpc.server.port");
    System.clearProperty("rpc.timeoutMillis");
  }

  private User user(int id) {
    User user = new User();
    user.setId(id);
    user.setName("user-" + id);
    return user;
  }

  @Test
  public void savesSingleAndTypedListThroughZooKeeper() {
    User single = user(1);
    single.setName("\u5f20\u4e09");
    Response r = (Response) caller.users.saveUser(single);
    assertEquals("\u5f20\u4e09", context.getBean(UserService.class).find(1).getName());
    assertTrue(r.successful());
    r = (Response) caller.users.saveUsers(Arrays.asList(user(2), user(3)));
    assertTrue(r.successful());
    assertEquals("user-3", context.getBean(UserService.class).find(3).getName());
    assertEquals(0, DefaultFuture.pendingCount());
  }

  @Test
  public void supportsNoArgsMultipleArgsOverloadsAndObjectMethods() {
    assertEquals("hello", caller.extra.hello());
    assertEquals("item7", caller.extra.join("item", 7));
    assertEquals("string:x", caller.extra.echo("x"));
    assertEquals("int:5", caller.extra.echo(5));
    assertTrue(caller.extra.equals(caller.extra));
    assertFalse(caller.extra.equals(new Object()));
    assertTrue(caller.extra.toString().contains("RPC proxy"));
  }

  @Test
  public void concurrentCallsNeverMixFramesOrResponses() throws Exception {
    assertEquals("hello", caller.extra.hello());
    ExecutorService pool = Executors.newFixedThreadPool(12);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        final int value = i;
        results.add(pool.submit(() -> caller.extra.join("call-", value)));
      }
      for (int i = 0; i < results.size(); i++)
        assertEquals("call-" + i, results.get(i).get(10, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
    assertEquals(0, DefaultFuture.pendingCount());
  }

  @Test
  public void failuresReturnImmediatelyAndTimeoutDoesNotLeak() {
    try {
      caller.extra.fail();
      fail("Expected remote error");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("expected failure"));
    }
    ClientRequest missing = new ClientRequest();
    missing.setCommand("missing");
    assertEquals("NO_METHOD", TcpClient.send(missing).getCode());
    System.setProperty("rpc.timeoutMillis", "30");
    try {
      caller.extra.slow(150);
      fail("Expected timeout");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("timed out"));
    }
    assertEquals(0, DefaultFuture.pendingCount());
  }

  @Test
  public void reconnectsAfterTcpConnectionIsClosed() {
    assertEquals("hello", caller.extra.hello());
    ChannelManager.get().close().syncUninterruptibly();
    assertEquals("hello", caller.extra.hello());
  }

  @Test
  public void serviceRemovalAndAdditionRefreshDiscovery() throws Exception {
    assertEquals("hello", caller.extra.hello());
    NettyInitial server = context.getBean(NettyInitial.class);
    server.destroy();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    boolean removed = false;
    while (System.nanoTime() < deadline) {
      try {
        ChannelManager.get();
      } catch (IllegalStateException e) {
        if (e.getMessage().contains("No RPC servers")) {
          removed = true;
          break;
        }
      }
      Thread.sleep(20);
    }
    assertTrue("Registry removal was not observed", removed);
    // Registration normally happens during context initialization; restore the service after this
    // manual restart.
    InitialMedium registry = context.getBean(InitialMedium.class);
    registry.postProcessAfterInitialization(context.getBean(ExtraService.class), "extra");
    registry.postProcessAfterInitialization(context.getBean(UserRemoteImpl.class), "users");
    server.start();
    deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    String result = null;
    while (System.nanoTime() < deadline) {
      try {
        result = caller.extra.hello();
        break;
      } catch (IllegalStateException e) {
        Thread.sleep(20);
      }
    }
    assertEquals("hello", result);
  }
}
