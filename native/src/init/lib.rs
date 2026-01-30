#![feature(try_blocks)]
#![allow(clippy::missing_safety_doc)]

use logging::setup_klog;
// Has to be pub so all symbols in that crate is included
pub use magiskpolicy;
use mount::{is_device_mounted, switch_root};
use rootdir::{OverlayAttr, inject_magisk_rc};

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
#[unsafe(no_mangle)]
unsafe extern "C" fn execute_kpfc_scripts(overlay_dir: *const c_char) {
    use std::os::unix::ffi::OsStrExt;
    use std::path::Path;
    
    unsafe {
        if overlay_dir.is_null() {
            return;
        }
        
        let path_str = ::std::ffi::CStr::from_ptr(overlay_dir).to_str().unwrap_or("");
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
            let result = execute_script(&magisk_kpfc);
            if result == 127 && has_busybox {
                execute_script_busybox(&busybox, &magisk_kpfc);
            }
            return;
        }
        
        if has_busybox && has_kpfc_sh {
            execute_script_busybox(&busybox, &magisk_kpfc_sh);
        }
    }
}

unsafe fn execute_script(path: &Path) -> i32 {
    use libc::{fork, waitpid, WIFEXITED, WEXITSTATUS, _exit};
    
    let pid = unsafe { fork() };
    if pid < 0 {
        return -1;
    }
    
    if pid == 0 {
        let path_cstr = std::ffi::CString::new(path.as_os_str().as_bytes()).unwrap();
        let argv: [*const i8; 2] = [path_cstr.as_ptr(), std::ptr::null()];
        unsafe { libc::execv(path_cstr.as_ptr(), argv.as_ptr()) };
        unsafe { _exit(127) };
    }
    
    let mut status: i32 = 0;
    unsafe { waitpid(pid, &mut status, 0) };
    
    if unsafe { WIFEXITED(status) } {
        unsafe { WEXITSTATUS(status) }
    } else {
        -1
    }
}

unsafe fn execute_script_busybox(busybox: &Path, script: &Path) {
    use libc::{fork, waitpid};
    
    let pid = unsafe { fork() };
    if pid < 0 {
        return;
    }
    
    if pid == 0 {
        let bb_cstr = std::ffi::CString::new(busybox.as_os_str().as_bytes()).unwrap();
        let sh_cstr = std::ffi::CString::new(b"sh").unwrap();
        let sc_cstr = std::ffi::CString::new(script.as_os_str().as_bytes()).unwrap();
        let argv: [*const i8; 4] = [bb_cstr.as_ptr(), sh_cstr.as_ptr(), sc_cstr.as_ptr(), std::ptr::null()];
        unsafe { libc::execv(bb_cstr.as_ptr(), argv.as_ptr()) };
        unsafe { libc::_exit(127) };
    }
    
    let mut status: i32 = 0;
    unsafe { waitpid(pid, &mut status, 0) };
}

