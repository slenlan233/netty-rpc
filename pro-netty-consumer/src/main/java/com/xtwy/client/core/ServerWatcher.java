package com.xtwy.client.core;

import com.xtwy.client.zk.ZookerFactory;
import com.xtwy.constant.Constants;
import java.util.*;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;

public final class ServerWatcher implements AutoCloseable {
  private final PathChildrenCache cache;

  public ServerWatcher() throws Exception {
    cache = new PathChildrenCache(ZookerFactory.create(), Constants.SERVER_PATH, true);
    cache.getListenable().addListener((client, event) -> refresh());
    cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
    refresh();
  }

  private void refresh() {
    List<String> nodes = new ArrayList<>();
    for (ChildData data : cache.getCurrentData()) {
      String path = data.getPath();
      nodes.add(path.substring(path.lastIndexOf('/') + 1));
    }
    Collections.sort(nodes);
    ChannelManager.updateServers(nodes);
  }

  public void close() throws Exception {
    cache.close();
  }
}
