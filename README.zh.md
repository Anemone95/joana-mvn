# 项目介绍

由于原版Joana使用旧版本WALA（1.4.4），其含有许多旧版本WALA的Bug，并且不支持新特性，如无法在缺失依赖的情况下构建CFG。

本项目对其进行改造，使Joana基于新版本WALA（1.5.3）并取代ant使用maven构建。

# 构建

```bash
mvn clean install -DskipTests
```

# 使用

使用如下代码导入jar包：

```xml
<dependency>
    <groupId>top.anemone.joana</groupId>
    <artifactId>joana-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

