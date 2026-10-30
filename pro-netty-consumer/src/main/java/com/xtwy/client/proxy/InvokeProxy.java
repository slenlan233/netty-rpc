package com.xtwy.client.proxy;

import com.alibaba.fastjson.JSON;
import com.xtwy.client.annotation.RemoteInvoke;
import com.xtwy.client.core.TcpClient;
import com.xtwy.client.param.*;
import com.xtwy.rpc.RpcRequest;
import java.lang.reflect.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class InvokeProxy implements BeanPostProcessor {
  public Object postProcessBeforeInitialization(Object bean, String name) {
    for (Class<?> type = bean.getClass(); type != Object.class; type = type.getSuperclass())
      for (Field field : type.getDeclaredFields()) {
        if (!field.isAnnotationPresent(RemoteInvoke.class)) continue;
        Class<?> service = field.getType();
        if (!service.isInterface())
          throw new IllegalArgumentException("@RemoteInvoke requires an interface: " + field);
        Object proxy =
            Proxy.newProxyInstance(
                service.getClassLoader(),
                new Class<?>[] {service},
                (instance, method, args) -> {
                  if (method.getDeclaringClass() == Object.class) {
                    switch (method.getName()) {
                      case "toString":
                        return "RPC proxy for " + service.getName();
                      case "hashCode":
                        return System.identityHashCode(instance);
                      case "equals":
                        return instance == args[0];
                      default:
                        throw new UnsupportedOperationException(method.toString());
                    }
                  }
                  ClientRequest request = new ClientRequest();
                  request.setCommand(RpcRequest.command(service, method));
                  request.setArguments(args);
                  Response response = TcpClient.send(request);
                  if (!response.successful())
                    throw new IllegalStateException(
                        "Remote error " + response.getCode() + ": " + response.getMsg());
                  if (method.getReturnType() == Void.TYPE) return null;
                  if (method.getReturnType().isAssignableFrom(Response.class)) return response;
                  return JSON.parseObject(
                      JSON.toJSONString(response.getResult()), method.getGenericReturnType());
                });
        try {
          field.setAccessible(true);
          field.set(bean, proxy);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException(e);
        }
      }
    return bean;
  }
}
