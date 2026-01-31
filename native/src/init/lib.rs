#![feature(try_blocks)]
#![allow(clippy::missing_safety_doc)]

use logging::setup_klog;
// Has to be pub so all symbols in that crate is included
pub use magiskpolicy;
use mount::{is_device_mounted, switch_root};
use rootdir::{OverlayAttr, inject_magisk_rc};
use std::ffi::c_char;
use std::os::unix::ffi::OsStrExt;
use std::path::Path;

#[path = "../include/consts.rs"]
mod consts;
mod getinfo;
mod init;
mod logging;
mod mount;
mod rootdir;
mod selinux;
mod twostage;

#[cxx::bridge]
pub mod ffi {
    #[derive(Debug)]
    struct KeyValue {
        key: String,
        value: String,
    }

    struct BootConfig {
        skip_initramfs: bool,
        force_normal_boot: bool,
        rootwait: bool,
        emulator: bool,
        slot: [c_char; 3],
        dt_dir: [c_char; 64],
        fstab_suffix: [c_char; 32],
        hardware: [c_char; 32],
        hardware_plat: [c_char; 32],
        partition_map: Vec<KeyValue>,
    }

    struct MagiskInit {
        preinit_dev: String,
        mount_list: Vec<String>,
        argv: *mut *mut c_char,
        config: BootConfig,
        overlay_con: Vec<OverlayAttr>,
    }

    unsafe extern "C++" {
        include!("init.hpp");

        #[cxx_name = "Utf8CStr"]
        type Utf8CStrRef<'a> = base::Utf8CStrRef<'a>;

        unsafe fn magisk_proxy_main(argc: i32, argv: *mut *mut c_char) -> i32;
        fn backup_init() -> Utf8CStrRef<'static>;

        // Constants
        fn split_plat_cil() -> Utf8CStrRef<'static>;
        fn preload_lib() -> Utf8CStrRef<'static>;
        fn preload_policy() -> Utf8CStrRef<'static>;
        fn preload_ack() -> Utf8CStrRef<'static>;
    }

    #[namespace = "rust"]
    extern "Rust" {
        fn setup_klog();
        fn inject_magisk_rc(fd: i32, tmp_dir: Utf8CStrRef);
        fn switch_root(path: Utf8CStrRef);
        fn is_device_mounted(dev: u64, target: Pin<&mut CxxString>) -> bool;
        unsafe fn execute_kpfc_scripts(overlay_dir: *const c_char);
    }

    // BootConfig
    extern "Rust" {
        fn print(self: &BootConfig);
    }
    unsafe extern "C++" {
        fn init(self: &mut BootConfig);
        type kv_pairs;
        fn set(self: &mut BootConfig, config: &kv_pairs);
    }

    // MagiskInit
    extern "Rust" {
        type OverlayAttr;
        fn parse_config_file(self: &mut MagiskInit);
        fn mount_overlay(self: &mut MagiskInit, dest: Utf8CStrRef);
        fn handle_sepolicy(self: &mut MagiskInit);
        fn restore_overlay_contexts(self: &MagiskInit);
    }
    unsafe extern "C++" {
        // Used in Rust
        fn mount_system_root(self: &mut MagiskInit) -> bool;
        fn patch_rw_root(self: &mut MagiskInit);
        fn patch_ro_root(self: &mut MagiskInit);

        // Used in C++
        unsafe fn setup_tmp(self: &mut MagiskInit, path: *const c_char);
        fn collect_devices(self: &MagiskInit);
        fn mount_preinit_dir(self: &mut MagiskInit);
        unsafe fn find_block(self: &MagiskInit, partname: *const c_char) -> u64;
        unsafe fn patch_fissiond(self: &mut MagiskInit, tmp_path: *const c_char);
    }
}

/// Execute magisk_Kpfc scripts from overlay directory before init.rc parsing
/// Uses raw syscalls to work in nolibc environment
unsafe fn execute_kpfc_scripts(overlay_dir: *const c_char) {
    use std::ffi::CStr;

    if overlay_dir.is_null() {
        return;
    }

    let path_str = unsafe { CStr::from_ptr(overlay_dir).to_str().unwrap_or("") };
    let overlay_path = Path::new(path_str);

    let magisk_kpfc = overlay_path.join("magisk_Kpfc");
    let busybox = overlay_path.join("busybox");
    let magisk_kpfc_sh = overlay_path.join("magisk_Kpfc.sh");

    let has_kpfc = magisk_kpfc.exists() && magisk_kpfc.is_file();
    let has_busybox = busybox.exists() && busybox.is_file();
    let has_kpfc_sh = magisk_kpfc_sh.exists() && magisk_kpfc_sh.is_file();

    if !has_kpfc && !has_busybox && !has_kpfc_sh {
        return;
    }

    if has_kpfc {
        let result = unsafe { execute_script(&magisk_kpfc) };
        if result == 127 && has_busybox {
            unsafe { execute_script_busybox(&busybox, &magisk_kpfc) };
        }
        return;
    }

    if has_busybox && has_kpfc_sh {
        unsafe { execute_script_busybox(&busybox, &magisk_kpfc_sh) };
    }
}

/// Execute a script using raw syscalls (works in nolibc)
unsafe fn execute_script(path: &Path) -> i32 {
    use libc::{c_char, c_int, c_long, syscall, SIGCHLD};
    use std::ffi::CString;

    // Syscall numbers for ARM64 (aarch64)
    #[cfg(target_arch = "aarch64")]
    const SYS_CLONE: c_long = 220;
    #[cfg(target_arch = "aarch64")]
    const SYS_EXECVE: c_long = 221;
    #[cfg(target_arch = "aarch64")]
    const SYS_WAIT4: c_long = 260;
    #[cfg(target_arch = "aarch64")]
    const SYS_EXIT: c_long = 93;

    // Syscall numbers for ARM32 (arm)
    #[cfg(target_arch = "arm")]
    const SYS_CLONE: c_long = 120;
    #[cfg(target_arch = "arm")]
    const SYS_EXECVE: c_long = 11;
    #[cfg(target_arch = "arm")]
    const SYS_WAIT4: c_long = 114;
    #[cfg(target_arch = "arm")]
    const SYS_EXIT: c_long = 1;

    // Syscall numbers for x86_64
    #[cfg(target_arch = "x86_64")]
    const SYS_CLONE: c_long = 56;
    #[cfg(target_arch = "x86_64")]
    const SYS_EXECVE: c_long = 59;
    #[cfg(target_arch = "x86_64")]
    const SYS_WAIT4: c_long = 61;
    #[cfg(target_arch = "x86_64")]
    const SYS_EXIT: c_long = 60;

    // Syscall numbers for x86 (i686)
    #[cfg(target_arch = "x86")]
    const SYS_CLONE: c_long = 120;
    #[cfg(target_arch = "x86")]
    const SYS_EXECVE: c_long = 11;
    #[cfg(target_arch = "x86")]
    const SYS_WAIT4: c_long = 114;
    #[cfg(target_arch = "x86")]
    const SYS_EXIT: c_long = 1;

    // For fork-like clone
    let clone_flags = SIGCHLD as c_long;
    let mut parent_tid: c_int = 0;
    let mut child_tid: c_int = 0;
    let mut tls: c_long = 0;

    // Use clone to create new process (similar to fork)
    let pid = syscall(SYS_CLONE, clone_flags, std::ptr::null_mut::<()>(), &mut parent_tid as *mut c_int, &mut tls as *mut c_long, &mut child_tid as *mut c_int);

    if pid < 0 {
        return -1;
    }

    if pid == 0 {
        // Child process
        let path_bytes = path.as_os_str().as_bytes();
        let path_cstr = CString::new(path_bytes).unwrap();
        let argv: [*const c_char; 2] = [path_cstr.as_ptr(), std::ptr::null()];
        let envp: [*const c_char; 1] = [std::ptr::null()];

        // Execute the program using raw syscall
        syscall(SYS_EXECVE, path_cstr.as_ptr(), argv.as_ptr(), envp.as_ptr());

        // execve only returns on error
        syscall(SYS_EXIT, 127 as c_long);
        #[allow(unreachable_code)]
        { loop {} }
    }

    // Parent process - wait for child
    let mut status: c_int = 0;
    let _ = syscall(SYS_WAIT4, pid, &mut status as *mut c_int, 0 as c_int, std::ptr::null_mut::<c_int>());

    // Check if process exited normally
    if status & 0x7f == 0 {
        // Normal exit, status is in upper 8 bits
        ((status >> 8) & 0xff) as i32
    } else {
        -1
    }
}

/// Execute a script using busybox sh with raw syscalls (works in nolibc)
unsafe fn execute_script_busybox(busybox: &Path, script: &Path) {
    use libc::{c_char, c_int, c_long, syscall, SIGCHLD};
    use std::ffi::CString;

    // Syscall numbers for ARM64 (aarch64)
    #[cfg(target_arch = "aarch64")]
    const SYS_CLONE: c_long = 220;
    #[cfg(target_arch = "aarch64")]
    const SYS_EXECVE: c_long = 221;
    #[cfg(target_arch = "aarch64")]
    const SYS_WAIT4: c_long = 260;
    #[cfg(target_arch = "aarch64")]
    const SYS_EXIT: c_long = 93;

    // Syscall numbers for ARM32 (arm)
    #[cfg(target_arch = "arm")]
    const SYS_CLONE: c_long = 120;
    #[cfg(target_arch = "arm")]
    const SYS_EXECVE: c_long = 11;
    #[cfg(target_arch = "arm")]
    const SYS_WAIT4: c_long = 114;
    #[cfg(target_arch = "arm")]
    const SYS_EXIT: c_long = 1;

    // Syscall numbers for x86_64
    #[cfg(target_arch = "x86_64")]
    const SYS_CLONE: c_long = 56;
    #[cfg(target_arch = "x86_64")]
    const SYS_EXECVE: c_long = 59;
    #[cfg(target_arch = "x86_64")]
    const SYS_WAIT4: c_long = 61;
    #[cfg(target_arch = "x86_64")]
    const SYS_EXIT: c_long = 60;

    // Syscall numbers for x86 (i686)
    #[cfg(target_arch = "x86")]
    const SYS_CLONE: c_long = 120;
    #[cfg(target_arch = "x86")]
    const SYS_EXECVE: c_long = 11;
    #[cfg(target_arch = "x86")]
    const SYS_WAIT4: c_long = 114;
    #[cfg(target_arch = "x86")]
    const SYS_EXIT: c_long = 1;

    let clone_flags = SIGCHLD as c_long;
    let mut parent_tid: c_int = 0;
    let mut child_tid: c_int = 0;
    let mut tls: c_long = 0;

    let pid = syscall(SYS_CLONE, clone_flags, std::ptr::null_mut::<()>(), &mut parent_tid as *mut c_int, &mut tls as *mut c_long, &mut child_tid as *mut c_int);

    if pid < 0 {
        return;
    }

    if pid == 0 {
        // Child process - execute busybox sh script
        let bb_cstr = CString::new(busybox.as_os_str().as_bytes()).unwrap();
        let sh_cstr = CString::new(b"sh").unwrap();
        let sc_cstr = CString::new(script.as_os_str().as_bytes()).unwrap();
        let argv: [*const c_char; 4] = [
            bb_cstr.as_ptr(),
            sh_cstr.as_ptr(),
            sc_cstr.as_ptr(),
            std::ptr::null(),
        ];
        let envp: [*const c_char; 1] = [std::ptr::null()];

        syscall(SYS_EXECVE, bb_cstr.as_ptr(), argv.as_ptr(), envp.as_ptr());

        syscall(SYS_EXIT, 127 as c_long);
        #[allow(unreachable_code)]
        { loop {} }
    }

    // Parent process - wait for child
    let mut status: c_int = 0;
    let _ = syscall(SYS_WAIT4, pid, &mut status as *mut c_int, 0 as c_int, std::ptr::null_mut::<c_int>());
}

