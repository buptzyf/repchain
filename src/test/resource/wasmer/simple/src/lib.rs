use std::ffi::{CStr, CString};
use std::mem;
use std::os::raw::{c_char, c_void};

extern {
    fn getValueSizeByKey(key_ptr: *mut c_char) -> i32;
    fn getValueByKey(key_ptr: *mut c_char, value_ptr: *mut c_void) -> i32;
    fn setValueByKey(key_ptr: *mut c_char, value_ptr: *mut c_void, value_size: usize) -> i32;
    fn logInfo(info: *mut c_char) -> i32;
}

#[no_mangle]
pub extern fn init(account1: *mut c_char, account1_balance: *mut c_char, account2: *mut c_char, account2_balance: *mut c_char) -> i32 {
    let account1_balance_size = unsafe {
        CStr::from_ptr(account1_balance).to_bytes().len()
    };
    let account2_balance_size = unsafe {
        CStr::from_ptr(account2_balance).to_bytes().len()
    };
    
    let res = unsafe { setValueByKey(account1, account1_balance as *mut c_void, account1_balance_size) };
    if res == -1 {
        return -1; 
    }
    let res = unsafe { setValueByKey(account2, account2_balance as *mut c_void, account2_balance_size) };
    return res;
}

#[no_mangle]
pub extern fn transfer(account_from: *mut c_char, account_to: *mut c_char, amount: *mut c_char) -> i32 {
    let account_from_balance_size = unsafe { getValueSizeByKey(account_from) };
    if account_from_balance_size == -1 {
        return -1;
    }
    let account_from_balance_ptr = vec![0; account_from_balance_size as usize + 1].as_ptr();
    let account_from_balance = unsafe { 
        let ret = getValueByKey(account_from, account_from_balance_ptr as *mut c_void);
        if ret == -1 {
            return -1;
        }
        let account_from_balance_str = CStr::from_ptr(account_from_balance_ptr).to_str().unwrap();
        let balance: u32 = account_from_balance_str.parse().unwrap();
        balance
    };

    let account_to_balance_size = unsafe { getValueSizeByKey(account_to) };
    if account_to_balance_size == -1 {
        return -1;
    }
    let account_to_balance_ptr = vec![0; account_to_balance_size as usize + 1].as_ptr();
    let account_to_balance = unsafe { 
        let ret = getValueByKey(account_to, account_to_balance_ptr as *mut c_void);
        if ret == -1 {
            return -1;
        }
        let account_to_balance_str = CStr::from_ptr(account_to_balance_ptr).to_str().unwrap();
        let balance: u32 = account_to_balance_str.parse().unwrap();
        balance
    };

    let amount_str = unsafe { CStr::from_ptr(amount).to_str().unwrap() };
    let amount_value: u32 = amount_str.parse().unwrap();

    if account_from_balance < amount_value {
        let account_from_name = unsafe { CStr::from_ptr(account_from).to_str().unwrap() };
        let msg = format!("The account: {} has no sufficient balance", account_from_name);
        let mut msg_vec: Vec<u8> = CString::new(msg).unwrap().into_bytes_with_nul();
        unsafe { logInfo(msg_vec.as_mut_ptr() as *mut c_char); }
        return -1;
    }

    let account_from_balance = account_from_balance - amount_value;
    let account_to_balance = account_to_balance + amount_value;
    let account_from_balance_string = account_from_balance.to_string();
    let account_from_balance_bytes = account_from_balance_string.as_bytes();
    let account_to_balance_string = account_to_balance.to_string();
    let account_to_balance_bytes = account_to_balance_string.as_bytes();

    let ret = unsafe { setValueByKey(account_from, account_from_balance_bytes.as_ptr() as *mut c_void, account_from_balance_bytes.len()) };
    if ret == -1 {
        return -1;
    }

    let ret = unsafe { setValueByKey(account_to, account_to_balance_bytes.as_ptr() as *mut c_void, account_to_balance_bytes.len()) };
    return ret;
}


#[no_mangle]
pub extern fn allocate(size: usize) ->  *mut c_void {
    let mut buffer = Vec::with_capacity(size);
    let ptr = buffer.as_mut_ptr();
    mem::forget(buffer);

    ptr as *mut c_void
}

#[no_mangle]
pub extern fn deallocate(ptr: *mut c_void, capacity: usize) {
    unsafe {
        let _ = Vec::from_raw_parts(ptr, 0, capacity);
    }
}


