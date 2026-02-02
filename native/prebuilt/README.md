# Prebuilt Binaries

此目录用于存放预编译的二进制文件。

## 必需文件（必须提供）

以下文件**必须**在 `native/prebuilt/arm64-v8a/` 目录中：

- `libmagisk.so` - 核心二进制
- `libmagiskboot.so` - Boot 镜像处理工具
- `libmagiskpolicy.so` - SELinux 策略工具
- `libinit-ld.so` - 动态链接器

这些文件**只能使用预编译版本**，不支持本地编译。

## 可选文件

### magiskinit

- `libmagiskinit.so` - 初始化工具

**行为**：
- ✅ 如果有预编译版本 → 使用预编译版本
- ✅ 如果没有预编译版本 → 自动编译 magiskinit

## 如何获取二进制文件

从官方 Magisk APK 提取：

```bash
# 下载官方 Magisk APK
wget https://github.com/topjohnwu/Magisk/releases/download/v27.0/Magisk-v27.0.apk

# 解压并提取
unzip Magisk-v27.0.apk -d temp
mkdir -p native/prebuilt/arm64-v8a
cp -r temp/lib/arm64-v8a/* native/prebuilt/arm64-v8a/

# 清理
rm -rf temp
```

## 构建优先级

### magiskinit
1. 优先使用 `native/prebuilt/$abi/libmagiskinit.so`
2. 如果没有，编译 `native/src/` 下的 magiskinit 源码
3. 输出到 `native/out/$abi/`

### 其他二进制
- 只使用 `native/prebuilt/$abi/` 下的预编译版本
- 如果缺少，构建会报错

## GitHub Actions 构建

在 GitHub Actions 上：
1. ✅ 使用预编译的 magisk, magiskboot, magiskpolicy, libinit-ld.so
2. ✅ 自动编译 magiskinit（如果预编译版本不存在）
3. ✅ 打包所有文件到 APK

这大大减少了 GitHub Actions 的构建时间！
