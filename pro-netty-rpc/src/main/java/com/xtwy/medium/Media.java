package com.xtwy.medium;

import com.alibaba.fastjson.JSON;
import com.xtwy.netty.handler.param.ServerRequest;
import com.xtwy.rpc.Response;
import java.lang.reflect.*;
import java.util.concurrent.*;

public final class Media {
  public static final ConcurrentMap<String, BeanMethod> beanMap = new ConcurrentHashMap<>();
  private static final Media INSTANCE = new Media();

  private Media() {}

  public static Media newInstance() {
    return INSTANCE;
  }

  public Response process(ServerRequest request) {
    if (request == null) return Response.failure(null, "BAD_REQUEST", "Request is null");
    if (request.getCommand() == null || request.getId() == null)
      return Response.failure(
          request.getId(), "BAD_REQUEST", "Request id and command are required");
    BeanMethod entry = beanMap.get(request.getCommand());
    if (entry == null)
      return Response.failure(
          request.getId(), "NO_METHOD", "Unknown method: " + request.getCommand());
    try {
      Method method = entry.getMethod();
      // Preserve List<User> and other generic arguments during JSON conversion.
      Type[] types = method.getGenericParameterTypes();
      Object[] arguments = request.getArguments();
      if (arguments.length != types.length)
        return Response.failure(
            request.getId(), "BAD_ARGUMENTS", "Expected " + types.length + " arguments");
      Object[] converted = new Object[types.length];
      for (int i = 0; i < types.length; i++)
        converted[i] = JSON.parseObject(JSON.toJSONString(arguments[i]), types[i]);
      Object value = method.invoke(entry.getBean(), converted);
      Response response;
      if (value instanceof Response) response = (Response) value;
      else {
        response = new Response();
        response.setResult(value);
      }
      response.setId(request.getId());
      return response;
    } catch (Exception e) {
      Throwable cause =
          e instanceof InvocationTargetException
              ? ((InvocationTargetException) e).getTargetException()
              : e;
      return Response.failure(
          request.getId(),
          "REMOTE_ERROR",
          cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }
  }
}
