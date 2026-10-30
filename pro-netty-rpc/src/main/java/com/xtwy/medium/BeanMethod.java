package com.xtwy.medium;

import java.lang.reflect.Method;

public class BeanMethod {
  private Object bean;
  private Method m;

  public Object getBean() {
    return bean;
  }

  public void setBean(Object bean) {
    this.bean = bean;
  }

  public Method getMethod() {
    return m;
  }

  public void setMethod(Method m) {
    this.m = m;
  }
}
