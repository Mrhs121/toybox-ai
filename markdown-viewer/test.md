# Markdown Viewer 测试文档

这是一个用于测试 Markdown Viewer 所有功能的文档。

## 文本格式

这是**粗体文本**，这是*斜体文本*，这是~~删除线文本~~。

这是`行内代码`，用于展示代码片段。

### 链接

访问 [GitHub](https://github.com) 或 [苹果官网](https://www.apple.com.cn)。

### 图片

![Swift Logo](https://developer.apple.com/swift/images/swift-logo.png)

## 列表

### 无序列表

- 项目一
- 项目二
  - 子项目 A
  - 子项目 B
- 项目三

### 有序列表

1. 第一步
2. 第二步
3. 第三步

### 任务列表

- [x] 已完成的任务
- [x] 另一个已完成的任务
- [ ] 待完成的任务
- [ ] 还有一个待完成的任务

## 代码块

### Swift 代码

```swift
import SwiftUI

struct ContentView: View {
    @State private var count = 0

    var body: some View {
        VStack {
            Text("Count: \(count)")
            Button("Increment") {
                count += 1
            }
        }
    }
}
```

### Python 代码

```python
def fibonacci(n):
    """Generate Fibonacci sequence up to n"""
    a, b = 0, 1
    while a < n:
        yield a
        a, b = b, a + b

for num in fibonacci(100):
    print(num)
```

### JavaScript 代码

```javascript
const debounce = (fn, delay) => {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
};
```

### Shell 脚本

```bash
#!/bin/bash
echo "Hello, World!"
for i in {1..5}; do
    echo "Iteration $i"
done
```

## 表格

| 语言 | 类型 | 用途 |
|------|------|------|
| Swift | 编译型 | iOS/macOS 开发 |
| Python | 解释型 | 数据科学、AI |
| JavaScript | 解释型 | Web 开发 |
| Go | 编译型 | 后端服务 |

### 对齐方式

| 左对齐 | 居中对齐 | 右对齐 |
|:-------|:-------:|-------:|
| 左 | 中 | 右 |
| 数据1 | 数据2 | 数据3 |

## 引用

> 这是一段引用文本。
>
> 可以包含多段内容。

> **注意**：这是一个重要的提示。

## 分割线

---

## 混合内容

### 任务与代码

以下是一个简单的任务列表实现：

```swift
struct TaskItem: Identifiable {
    let id = UUID()
    var title: String
    var isCompleted: Bool
}
```

### 表格与链接

| 项目 | 链接 | 状态 |
|------|------|------|
| SwiftUI | [官方文档](https://developer.apple.com/xcode/swiftui/) | 活跃 |
| Combine | [官方文档](https://developer.apple.com/documentation/combine) | 活跃 |
| UIKit | [官方文档](https://developer.apple.com/documentation/uikit) | 维护中 |

## 结尾

以上是 Markdown Viewer 支持的所有主要格式。如果你能看到所有内容正确渲染，说明 viewer 工作正常！
