package com.xtwy.basic.controller;

import com.xtwy.basic.service.BasicService;
import com.xtwy.client.core.TcpClient;
import org.springframework.context.annotation.*;

@ComponentScan("com.xtwy")
public class BasicController {
  public static void main(String[] args) {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(BasicController.class)) {
      context.getBean(BasicService.class).testSaveUser();
    } finally {
      TcpClient.close();
    }
  }
}
