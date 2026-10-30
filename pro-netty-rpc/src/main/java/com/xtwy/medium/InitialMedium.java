package com.xtwy.medium;

import com.xtwy.annotation.Remote;
import com.xtwy.rpc.RpcRequest;
import java.lang.reflect.Method;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

@Component
public class InitialMedium implements BeanPostProcessor {
  public Object postProcessAfterInitialization(Object bean, String name) {
    if (AnnotationUtils.findAnnotation(bean.getClass(), Remote.class) == null) return bean;
    for (Class<?> service : ClassUtils.getAllInterfacesForClass(bean.getClass()))
      for (Method method : service.getMethods()) {
        BeanMethod entry = new BeanMethod();
        entry.setBean(bean);
        entry.setMethod(method);
        String command = RpcRequest.command(service, method);
        BeanMethod old = Media.beanMap.putIfAbsent(command, entry);
        if (old != null && old.getBean() != bean)
          throw new IllegalStateException("Duplicate RPC service: " + command);
      }
    return bean;
  }
}
