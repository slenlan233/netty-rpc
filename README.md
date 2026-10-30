# netty-rpc

项目保留 Spring + Netty + ZooKeeper 的结构。服务端和客户端都必须连接 ZooKeeper，没有直连或绕过注册中心的运行模式。

## 环境与运行

使用 JDK 17 和 Maven。五个工程保持为相邻目录，先启动你自己的 ZooKeeper（默认 `127.0.0.1:2181`）。根节点 `/netty` 会自动创建。

克隆仓库后，进入 `pro-basic` 目录构建全部工程并运行回归测试：

```powershell
cd pro-basic
.\run.ps1 build
```

然后分别在两个终端执行：

```powershell
# 终端一：服务提供方，注册成功后打印 RPC server ready
.\run.ps1 server

# 终端二：客户端，通过 ZooKeeper 发现服务并保存用户
.\run.ps1 client
```

成功后客户端打印包含 `code: "00000"` 和用户数据的 JSON，然后退出。服务端持续运行，按 Ctrl+C 关闭。

如果 PowerShell 阻止执行脚本，可以使用 `powershell -ExecutionPolicy Bypass -File .\run.ps1 build`，server/client 同理。

自定义 ZooKeeper、服务端口和对外公布的主机地址：

```powershell
.\run.ps1 server -ZkAddress 127.0.0.1:2181 -Port 8888 -AdvertiseHost 127.0.0.1
.\run.ps1 client -ZkAddress 127.0.0.1:2181
```

跨机器运行时，`AdvertiseHost` 必须是客户端能够访问到的服务端 IP 或主机名。客户端不配置服务端地址。

也可以在 IDEA 中导入仓库根目录的 `pom.xml`，依次运行 `pro-user-core` 的 `com.xtwy.server.SpringServer` 和 `pro-basic` 的 `com.xtwy.basic.controller.BasicController`。VM 参数可设置 `-Drpc.zk.address=host:2181`；服务端还支持 `-Drpc.server.port=8888`、`-Drpc.server.host=host`、`-Drpc.server.weight=2`，客户端支持 `-Drpc.timeoutMillis=3000`。

构建脚本将依赖缓存放在 `pro-basic/.build/m2`，可执行产物和运行依赖分别位于各业务工程的 `target` 和 `target/dependency`。

## 工程与调用链

- `pro-user-api`：唯一的业务接口、User 模型、请求和响应协议。
- `pro-netty-consumer`：JDK 接口代理、服务发现、连接复用/重连、同步等待。
- `pro-netty-rpc`：服务注册、方法路由、参数转换、业务执行和响应。
- `pro-user-core`：业务实现及服务端入口。
- `pro-basic`：调用方示例及统一构建入口。

```text
BasicService → @RemoteInvoke 代理 → ZooKeeper 发现地址 → Netty TCP
→ ServerHandler → Media → UserRemoteImpl → UserService
→ 响应携带原请求 ID → 客户端完成对应等待 → 返回业务调用方
```

请求 ID 在客户端进程内递增；方法标识包含接口名、方法名和参数类型，支持无参、多参和重载。消息使用 UTF-8 JSON，以换行分帧，整条消息一次写入。服务端保留泛型参数类型。失败响应也携带请求 ID；客户端代理将远程错误转换成异常。

每台服务复用一个连接，权重作用于轮询选择；连接断开后在后续调用中重新建立。ZooKeeper 子节点变化会更新连接集合。服务注册为临时顺序节点，Curator 管理会话恢复后的重新注册。没有可用服务或连接失败时给出明确错误，不会自动重试业务调用，避免重复执行保存操作。

旧的重复接口和过时网络练习入口已经删除，避免误启动旧版本。两端需一起重新构建，旧请求格式不兼容新协议。

`UserService` 使用内存保存用户，便于验证列表类型和业务调用；重启后清空，没有连接 MySQL。

## 验证

```powershell
.\run.ps1 test
```

测试会启动随机端口的临时 ZooKeeper 和真实 TCP 服务，结束后清理，不需要手动启动 ZooKeeper。测试覆盖请求匹配、超时清理、心跳、泛型列表、无参/多参/重载、并发调用、错误响应、断线重连及服务上下线。
