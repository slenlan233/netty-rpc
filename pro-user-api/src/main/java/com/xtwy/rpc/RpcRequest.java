package com.xtwy.rpc;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

public class RpcRequest {
  private static final AtomicLong IDS = new AtomicLong();
  private Long id = IDS.incrementAndGet();
  private String command;
  private Object[] arguments = new Object[0];

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public Object[] getArguments() {
    return arguments;
  }

  public void setArguments(Object[] arguments) {
    this.arguments = arguments == null ? new Object[0] : arguments;
  }

  public static String command(Class<?> service, Method method) {
    StringBuilder key =
        new StringBuilder(service.getName()).append('.').append(method.getName()).append('(');
    Class<?>[] types = method.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      if (i > 0) key.append(',');
      key.append(types[i].getName());
    }
    return key.append(')').toString();
  }
}
