# 博客系统后端（blog-backend）

基于 Spring Boot 3 + MyBatis Plus + Redis 的博客系统后端服务，提供前台内容浏览与后台内容管理完整能力。作为毕业设计项目，重点实践了缓存优化、JWT 鉴权、统一异常处理与逻辑删除等工程化能力。

## 技术栈

| 分类 | 选型 | 版本 |
| --- | --- | --- |
| 基础框架 | Spring Boot | 3.2.5 |
| 语言版本 | JDK | 17 |
| ORM | MyBatis-Plus | 3.5.6 |
| 数据库 | MySQL | 8.x |
| 缓存 | Redis | - |
| 鉴权 | JJWT | 0.12.5 |
| 参数校验 | spring-boot-starter-validation | - |
| 工具 | Lombok | - |

## 项目结构

```
src/main/java/com/blog
├── BlogApplication.java        # 启动类
├── common/                     # 通用层
│   ├── Result.java             #   统一响应体
│   ├── BusinessException.java  #   业务异常
│   └── GlobalExceptionHandler.java # 全局异常处理
├── config/                     # 配置层
│   ├── WebConfig.java          #   CORS + 拦截器注册
│   ├── RedisConfig.java        #   RedisTemplate 序列化配置
│   └── MybatisPlusConfig.java #   分页插件等配置
├── interceptor/
│   └── JwtInterceptor.java     # JWT 鉴权拦截器
├── controller/                 # 控制层
│   ├── AuthController.java     #   /api/auth 登录鉴权
│   ├── FrontController.java    #   /api 前台接口
│   └── AdminController.java    #   /api/admin 后台接口
├── service/                    # 业务层
│   ├── ArticleService.java
│   ├── CategoryService.java
│   ├── TagService.java
│   ├── CommentService.java
│   ├── AuthService.java
│   └── StatsService.java
├── mapper/                     # 数据访问层
├── entity/                     # 实体（Article/Category/Tag/Comment/User/ArticleTag）
├── dto/                        # 请求与视图对象
└── util/
    └── JwtUtil.java            # JWT 生成与校验
```

## 功能特性

### 前台（`/api`，无需登录）
- 文章列表分页查询（支持按分类、标签、关键字筛选）
- 最新文章列表
- 文章详情（自动累加浏览量）
- 分类列表 / 标签列表
- 文章评论列表与发表评论

### 后台（`/api/admin`，需 JWT）
- 数据看板统计（`/stats`）
- 文章管理：新增、编辑、删除（逻辑删除）、回收站、恢复、永久删除
- 分类 / 标签的增删改查
- 评论列表与删除

### 鉴权
- 基于 JWT，登录后下发 Token，前端请求头携带 `Authorization: Bearer <token>`
- `JwtInterceptor` 拦截 `/api/admin/**`，放行 `/api/auth/**`
- Token 校验失败统一返回 401

### 缓存设计
| 缓存 Key | 说明 | TTL |
| --- | --- | --- |
| `blog:article:{id}` | 文章详情 | 30 分钟 |
| `blog:articles:latest:{limit}` | 最新文章列表 | 10 分钟 |
| `blog:categories` | 分类列表 | 1 小时 |
| `blog:tags` | 标签列表 | 1 小时 |

数据发生变更时（新增、修改、删除）会主动清除对应缓存，避免脏读。

### 工程化要点
- **统一响应**：所有接口通过 `Result<T>` 包装，包含 `code`/`message`/`data`
- **全局异常**：`GlobalExceptionHandler` 捕获业务异常与参数校验异常，统一输出错误结构
- **逻辑删除**：MyBatis-Plus `@TableLogic`，`deleted=1` 表示已删除
- **浏览量原子自增**：使用 `UPDATE article SET view_count = view_count + 1 WHERE id = ?`，避免并发丢失
- **避免 N+1**：列表查询采用批量 ID 收集 + IN 查询 + 内存组装 VO

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.x
- Redis 5.x+

### 数据库准备
创建数据库 `blog_db`，并导入业务表（article / category / tag / article_tag / comment / user）。

### 修改配置
编辑 [src/main/resources/application.yml](src/main/resources/application.yml)，按实际环境调整：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379

blog:
  jwt:
    secret: blog-graduation-project-secret-key-2026
    expiration: 86400000   # Token 有效期，单位毫秒（默认 24 小时）
```

### 构建与运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/blog-backend-1.0.0.jar
```

或在 IDE 中直接运行 [BlogApplication.java](src/main/java/com/blog/BlogApplication.java)。

服务默认监听 `http://localhost:8080`。

## 接口概览

### 鉴权
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/auth/info` | 根据 Token 获取当前用户信息 |

### 前台
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/articles` | 文章分页列表 |
| GET | `/api/articles/latest` | 最新文章 |
| GET | `/api/articles/{id}` | 文章详情 |
| GET | `/api/categories` | 分类列表 |
| GET | `/api/tags` | 标签列表 |
| GET | `/api/articles/{id}/comments` | 评论列表 |
| POST | `/api/articles/{id}/comments` | 发表评论 |

### 后台（需 Token）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/stats` | 统计看板 |
| GET/POST/PUT/DELETE | `/api/admin/articles[/{id}]` | 文章增删改查 |
| GET | `/api/admin/recycle/articles` | 回收站 |
| PUT | `/api/admin/articles/{id}/restore` | 恢复文章 |
| DELETE | `/api/admin/articles/{id}/permanent` | 永久删除 |
| GET/POST/PUT/DELETE | `/api/admin/categories[/{id}]` | 分类管理 |
| GET/POST/PUT/DELETE | `/api/admin/tags[/{id}]` | 标签管理 |
| GET | `/api/admin/comments` | 评论列表 |
| DELETE | `/api/admin/comments/{id}` | 删除评论 |

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

业务异常或校验失败时 `code` 非 200，并通过 `message` 返回错误描述。
