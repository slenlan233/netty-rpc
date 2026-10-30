package com.xtwy.client.core;

import com.xtwy.client.param.ClientRequest;
import com.xtwy.client.param.Response;
import io.netty.channel.Channel;
import java.util.concurrent.*;

public final class DefaultFuture {
  private static final ConcurrentMap<Long, DefaultFuture> PENDING = new ConcurrentHashMap<>();
  private final Long id;
  private final Channel channel;
  private final CompletableFuture<Response> result = new CompletableFuture<>();

  public DefaultFuture(ClientRequest request, Channel channel) {
    this.id = request.getId();
    this.channel = channel;
    if (PENDING.putIfAbsent(id, this) != null)
      throw new IllegalStateException("Duplicate request ID: " + id);
  }

  public Response get(long timeoutMillis) {
    try {
      return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("RPC interrupted", e);
    } catch (TimeoutException e) {
      throw new IllegalStateException("RPC timed out after " + timeoutMillis + " ms, id=" + id, e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("RPC failed, id=" + id, e.getCause());
    } finally {
      PENDING.remove(id, this);
    }
  }

  public void fail(Throwable cause) {
    PENDING.remove(id, this);
    result.completeExceptionally(cause);
  }

  public static void receive(Response response) {
    if (response == null || response.getId() == null) return;
    DefaultFuture f = PENDING.remove(response.getId());
    if (f != null) f.result.complete(response);
  }

  public static void disconnected(Channel channel) {
    for (DefaultFuture f : PENDING.values())
      if (f.channel == channel) f.fail(new IllegalStateException("RPC connection closed"));
  }

  public static void closeAll() {
    for (DefaultFuture f : PENDING.values()) f.fail(new IllegalStateException("RPC client closed"));
  }

  public static int pendingCount() {
    return PENDING.size();
  }
}
