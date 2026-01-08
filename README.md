# 待办事项应用

一个实用的 Android 待办事项管理应用，采用 MVVM 架构，使用 Kotlin 开发。

## 📱 应用功能

- ✅ **任务管理**：添加、编辑、删除任务
- ✅ **任务列表**：查看未完成和已完成的任务
- ✅ **优先级设置**：支持低、中、高三个优先级
- ✅ **完成状态**：标记任务为完成/未完成
- ✅ **数据持久化**：使用 Room 数据库本地存储

## 🏗️ 技术架构

本项目采用 **MVVM（Model-View-ViewModel）架构模式**：

- **UI Layer（视图层）**：Activity、Fragment，负责 UI 展示
- **ViewModel Layer（视图模型层）**：管理 UI 状态和业务逻辑
- **Repository Layer（数据仓库层）**：统一数据访问入口
- **Data Layer（数据层）**：Room 数据库

## 📁 项目结构

```
app/src/main/java/com/example/myapplication/
├── MyApplication.kt           # Application 类
├── di/                        # 依赖注入
│   └── AppContainer.kt       # 依赖容器
├── model/                     # 数据模型
│   └── Task.kt               # 任务数据模型
├── data/                      # 数据层
│   ├── TaskEntity.kt         # Room 实体类
│   ├── TaskDao.kt            # 数据访问对象
│   └── AppDatabase.kt        # Room 数据库
├── repository/                # 数据仓库层
│   └── TaskRepository.kt     # 任务数据仓库
├── viewmodel/                 # 视图模型层
│   ├── TaskListViewModel.kt  # 任务列表 ViewModel
│   ├── TaskDetailViewModel.kt # 任务详情 ViewModel
│   └── ViewModelFactory.kt   # ViewModel 工厂
└── ui/                        # UI 层
    ├── MainActivity.kt       # 主界面
    ├── TaskListActivity.kt   # 任务列表页
    └── TaskEditActivity.kt   # 任务编辑页
```

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 11 或更高版本
- Android SDK 24 或更高版本

### 运行项目

1. 克隆或下载项目
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. 点击运行按钮

## 📚 涵盖的 Android 知识点

在实现这个实用应用的过程中，自然涵盖了以下 Android 开发知识点：

- **Activity 生命周期**
- **MVVM 架构模式**
- **Room 数据库**：本地数据持久化
- **RecyclerView**：列表展示
- **ViewModel**：UI 状态管理
- **LiveData / StateFlow**：响应式数据
- **Kotlin 协程**：异步操作
- **ConstraintLayout**：布局系统
- **Material Design 组件**

## 📖 学习文档

详细的学习文档请查看：[项目学习文档](./project_docs/)

## 🔧 技术栈

- **开发语言**：Kotlin
- **最低 SDK**：24 (Android 7.0)
- **目标 SDK**：35 (Android 15)
- **主要依赖**：
  - AndroidX Core KTX
  - Material Design Components
  - ConstraintLayout
  - RecyclerView
  - Room Database
  - Kotlin Coroutines
  - Lifecycle & ViewModel

## 📄 许可证

本项目仅用于学习目的。

---

**祝你使用愉快！** 🚀
