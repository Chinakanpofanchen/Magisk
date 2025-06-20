#include "init.hpp"
#include <base.hpp>
#include <vector>
#include <dirent.h>

// 检查/data是否为空目录
bool is_data_empty() {
    DIR *dir = opendir("/data");
    if (!dir) return true; // 目录不存在视为空
    
    bool empty = true;
    while (dirent *entry = readdir(dir)) {
        if (strcmp(entry->d_name, ".") != 0 && 
            strcmp(entry->d_name, "..") != 0) {
            empty = false;
            break;
        }
    }
    closedir(dir);
    return empty;
}

void MagiskInit::patch_vendor_fstab() {
    // 只有在/data为空时才修改
    if (!is_data_empty()) {
        LOGI("Skipping fstab patch: /data not empty\n");
        return;
    }
    
    const char *fstab_path = "/vendor/etc/fstab.qcom";
    char overlay_path[PATH_MAX];
    snprintf(overlay_path, sizeof(overlay_path), "%s%s", ROOTOVL, fstab_path);
    
    // 创建目录结构
    mkdirs(dirname(overlay_path), 0755);
    
    // 读取原始文件
    mmap_data orig(fstab_path);
    if (!orig) return;
    
    // 修改内容
    string patched(orig.buf(), orig.sz());
    replace_all(patched, "fileencryption", "encryption");
    
    // 写入覆盖层
    int fd = xopen(overlay_path, O_WRONLY | O_CREAT | O_TRUNC, 0);
    write(fd, patched.data(), patched.size());
    close(fd);
    
    // 克隆属性
    clone_attr(fstab_path, overlay_path);
    
    // 记录需要挂载的文件
    fstab_patches.push_back(fstab_path);
    LOGI("Patched fstab.qcom\n");
}

void MagiskInit::mount_fstab_patches() {
    for (const auto &path : fstab_patches) {
        char overlay_path[PATH_MAX];
        snprintf(overlay_path, sizeof(overlay_path), "%s%s", ROOTOVL, path);
        
        if (access(overlay_path, F_OK) == 0) {
            xmount(overlay_path, path, nullptr, MS_BIND, nullptr);
        }
    }
}
