package com.xtwy.user.remote;

import com.xtwy.user.model.User;
import java.util.List;

public interface UserRemote {
  public Object saveUser(User user);

  public Object saveUsers(List<User> users);
}
