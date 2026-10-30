package com.xtwy.user.service;

import com.xtwy.user.model.User;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final ConcurrentMap<Integer, User> users = new ConcurrentHashMap<>();

  public void save(User user) {
    if (user == null || user.getId() == null)
      throw new IllegalArgumentException("User id is required");
    User copy = new User();
    copy.setId(user.getId());
    copy.setName(user.getName());
    users.put(copy.getId(), copy);
  }

  public void saveList(List<User> users) {
    if (users == null) throw new IllegalArgumentException("Users are required");
    for (User user : users) save(user);
  }

  public User find(Integer id) {
    return users.get(id);
  }
}
