package com.xtwy.user.remote;

import com.xtwy.annotation.Remote;
import com.xtwy.user.model.User;
import com.xtwy.user.service.UserService;
import com.xtwy.util.ResponseUtil;
import java.util.List;
import javax.annotation.Resource;

@Remote
public class UserRemoteImpl implements UserRemote {

  @Resource private UserService userService;

  public Object saveUser(User user) {
    userService.save(user);
    return ResponseUtil.createSuccessResult(user);
  }

  public Object saveUsers(List<User> users) {
    userService.saveList(users);
    return ResponseUtil.createSuccessResult(users);
  }
}
