package com.xtwy.rpc;

public class Response {
  private Long id;
  private Object result;
  private String code = "00000";
  private String msg;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Object getResult() {
    return result;
  }

  public void setResult(Object result) {
    this.result = result;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }

  public boolean successful() {
    return "00000".equals(code);
  }

  public static Response failure(Long id, String code, String message) {
    Response r = new Response();
    r.setId(id);
    r.setCode(code);
    r.setMsg(message);
    return r;
  }
}
