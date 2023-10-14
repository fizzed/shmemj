use std::time::Duration;
use jni::JNIEnv;
use jni::objects::{JObject, JString, JValue};
use jni::sys::{jstring, jlong, jboolean, JNI_TRUE, JNI_FALSE};
use jni::sys::jobject;
use raw_sync::events::{Event, EventImpl, EventInit, EventState};
use raw_sync::Timeout;
use shared_memory::{Shmem, ShmemConf, ShmemError};

fn to_jboolean(v: bool) -> jboolean {
    if v {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

fn handle_shmem_error<T>(env: &mut JNIEnv, result: &Result<T,ShmemError>) -> bool {
    if result.is_err() {
        let error = result.as_ref().err().unwrap();
        let error_message = format!("{:?}: {}", error, error);
        env.throw(error_message).unwrap();
        return true;
    } else {
        return false;
    }
}

//
// SharedMemoryFactory native methods
//

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemoryFactory_doCreate<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) -> jobject {

    let size = env.get_field(&target, "size", "J")
        .unwrap()
        .j()
        .unwrap();

    // println!("doCreate(): size={}", size);

    let shmem_result = ShmemConf::new()
        .size(size as usize)
        .create();

    if handle_shmem_error(&mut env, &shmem_result) {
        return JObject::null().into_raw();
    }

    return create_shmem_object(&mut env, shmem_result.unwrap());
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemoryFactory_doOpen<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) -> jobject {

    let size = env.get_field(&target, "size", "J")
        .unwrap()
        .j()
        .unwrap();

    let os_id = JString::from(env.get_field(&target, "osId", "Ljava/lang/String;")
        .unwrap()
        .l()
        .unwrap());

    let os_id_jstr = unsafe { env.get_string_unchecked(&os_id).unwrap() };
    let os_id = os_id_jstr.to_str().unwrap();

    // println!("doOpen(): size={}, os_id={}", size, os_id);

    let shmem_result = ShmemConf::new()
        .size(size as usize)
        .os_id(os_id)
        .open();

    if handle_shmem_error(&mut env, &shmem_result) {
        return JObject::null().into_raw();
    }

    return create_shmem_object(&mut env, shmem_result.unwrap());
}

fn create_shmem_object(env: &mut JNIEnv, shmem: Shmem) -> jobject {
    //let shmem_manually_dropped = ManuallyDrop::new(shmem);

    // println!("create(): shmem_boxed ptr={:p}", &shmem_boxed);

    // this moves it to the heap, but then leaks it back so we can keep it around
    let shmem_leaked = Box::leak(Box::new(shmem));

    // println!("create(): shmem ptr={:p}, osid={}, byteptr={:p}", shmem_leaked, shmem_leaked.get_os_id(), shmem_leaked.as_ptr());

    let shared_memory_class = env.find_class("com/fizzed/shmemj/SharedMemory").unwrap();

    //println!("Found shmem class: {:?}", shared_memory_class);

    let shmem_jobj = env.new_object(&shared_memory_class, "()V", &[])
        .unwrap();

    let ptr = shmem_leaked as *const Shmem as jlong;

    // println!("create(): ptr was {}", ptr);

    // primitive type of a long is a J
    env.set_field(&shmem_jobj, "ptr", "J", JValue::Long(ptr))
        .unwrap();

    return shmem_jobj.into_raw();
}

//
// SharedMemory native methods
//

fn get_shmem_co_object<'local>(env: &mut JNIEnv, target: &JObject) -> Option<&'local mut Shmem> {

    // the "ptr" field on the SharedMemory class instance is the address of the companion object in rust
    let ptr = env.get_field(&target, "ptr", "J")
        .unwrap()
        .j()
        .unwrap();

    if ptr == 0 {
        return None;
    }

    let shmem = unsafe { &mut *(ptr as *mut Shmem) };

    return Some(shmem);
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_destroy<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) {

    let shmem = get_shmem_co_object(&mut env, &target);

    if shmem.is_none() {
        return; // nothing to do
    }

    unsafe {
        let shmem_boxed = Box::from_raw(shmem.unwrap());
        drop(shmem_boxed);
    }

    // clear out pointer
    env.set_field(&target, "ptr", "J", JValue::Long(0))
        .unwrap();
}

fn handle_shmem_invalid(env: &mut JNIEnv, shmem: &Option<&mut Shmem>) -> bool {
    if shmem.is_none() {
        env.throw("SharedMemory is invalid (no native resource attached)").unwrap();
        return true;
    } else {
        return false;
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_isOwner<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) -> jboolean {

    let shmem = get_shmem_co_object(&mut env, &target);

    if handle_shmem_invalid(&mut env, &shmem) {
        return JNI_FALSE;   // fake return since exception was thrown
    }

    let is_owner = shmem.unwrap().is_owner();

    return to_jboolean(is_owner);
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_getOsId<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) -> jstring {

    let shmem = get_shmem_co_object(&mut env, &target);

    if handle_shmem_invalid(&mut env, &shmem) {
        return JObject::null().into_raw();   // fake return since exception was thrown
    }

    let os_id = shmem.unwrap().get_os_id();

    let output = env.new_string(os_id)
        .expect("Couldn't create java string!");

    return output.into_raw();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_getSize<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) -> jlong {

    let shmem = get_shmem_co_object(&mut env, &target);

    if handle_shmem_invalid(&mut env, &shmem) {
        return -1;   // fake return since exception was thrown
    }

    let size = shmem.unwrap().len();

    return size as jlong;
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_doNewByteBuffer<'local>(mut env: JNIEnv<'local>, target: JObject<'local>, offset: jlong, length: jlong) -> jobject {
    let shmem = get_shmem_co_object(&mut env, &target);

    if handle_shmem_invalid(&mut env, &shmem) {
        return JObject::null().into_raw();
    }

    unsafe {
        let mem_ptr = shmem.unwrap().as_ptr().offset(offset as isize);

        let byte_buffer = env.new_direct_byte_buffer(mem_ptr, length as usize).unwrap();

        return byte_buffer.into_raw();
    }
}

fn create_event_object(env: &mut JNIEnv, event_boxed: Box<dyn EventImpl>, event_size: usize) -> jobject {
    // since its already boxed, we'll leak it out, then make it manually dropped
    let event = Box::leak(event_boxed);

    // println!("newCondition(): event ptr={:p}, size={}", event, event_size);

    let shared_condition_class = env.find_class("com/fizzed/shmemj/SharedCondition").unwrap();

    let shcond_obj = env.new_object(&shared_condition_class, "()V", &[])
        .unwrap();

    // apparently traits like EventImpl are "fat" and we have to get a pointer to them
    // https://users.rust-lang.org/t/sending-a-boxed-trait-over-ffi/21708/4
    let event_ptr = event as *mut dyn EventImpl;
    // so we'll then box up the pointer, then get a reference to that
    let event_ptr_boxed = Box::new(event_ptr);
    let event_ptr_raw = Box::into_raw(event_ptr_boxed);
    let ptr = event_ptr_raw as usize;

    // println!("newCondition(): ptr was {}", ptr);

    // primitive type of a long is a J
    env.set_field(&shcond_obj, "ptr", "J", JValue::Long(ptr as jlong))
        .unwrap();

    env.set_field(&shcond_obj, "size", "J", JValue::Long(event_size as jlong))
        .unwrap();

    return shcond_obj.into_raw();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_doNewCondition<'local>(mut env: JNIEnv<'local>, target: JObject<'local>, offset: jlong, auto_reset: jboolean) -> jobject {

    let shmem = get_shmem_co_object(&mut env, &target);

    if handle_shmem_invalid(&mut env, &shmem) {
        return JObject::null().into_raw();
    }

    //
    // create an "Event" that we'll associate with a SharedCondition
    //
    //
    unsafe {
        let mem_ptr = shmem.unwrap().as_ptr().offset(offset as isize);

        let auto_reset_r = JValue::Bool(auto_reset).z().unwrap();

        let (event_boxed, event_size) = Event::new(mem_ptr, auto_reset_r).unwrap();

        return create_event_object(&mut env, event_boxed, event_size);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_doExistingCondition<'local>(mut env: JNIEnv<'local>, target: JObject<'local>, offset: jlong) -> jobject {

    let shmem = get_shmem_co_object(&mut env, &target);

    if handle_shmem_invalid(&mut env, &shmem) {
        return JObject::null().into_raw();
    }

    //
    // create an "Event" that we'll associate with a SharedCondition
    //
    //

    unsafe {
        let mem_ptr = shmem.unwrap().as_ptr().offset(offset as isize);

        let (event_boxed, event_size) = Event::from_existing(mem_ptr).unwrap();

        return create_event_object(&mut env, event_boxed, event_size);
    }
}

//
// SharedCondition native methods
//

fn get_event_co_object<'local>(env: &mut JNIEnv, target: &JObject) -> Option<&'local mut dyn EventImpl> {
    // the "ptr" field on the SharedMemory class instance is the address of the companion object in rust
    let ptr = env.get_field(&target, "ptr", "J")
        .unwrap()
        .j()
        .unwrap();

    // pointer should NOT be zero, if it is then we don't have a co-object
    if ptr == 0 {
        env.throw("SharedCondition is invalid (no native resource attached)").unwrap();
        return None;
    }

    // note: we had to double box this
    // https://users.rust-lang.org/t/sending-a-boxed-trait-over-ffi/21708/4
    unsafe {
        let event_ptr_raw = ptr as *mut *mut dyn EventImpl;
        let event_ptr_boxed = Box::from_raw(event_ptr_raw);
        let event = &mut *(*event_ptr_boxed.as_ref());

        // HACK: we do not want the box being dropped yet, so we'll into_raw it to prevent that
        // from happening until we are ready for it to be dropped
        let _ = Box::into_raw(event_ptr_boxed);

        return Some(event);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedCondition_destroy<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) {

    let ptr = env.get_field(&target, "ptr", "J")
        .unwrap()
        .j()
        .unwrap();

    if ptr == 0 {
        return;     // nothing to do
    }

    unsafe {
        let event_ptr_raw = ptr as *mut *mut dyn EventImpl;
        let event_ptr_boxed = Box::from_raw(event_ptr_raw);
        let event = &mut *(*event_ptr_boxed.as_ref());
        let event_box = Box::from_raw(event);
        drop(event_box);
        drop(event_ptr_boxed);
    }

    // clear out pointer
    env.set_field(&target, "ptr", "J", JValue::Long(0))
        .unwrap();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedCondition_awaitMillis<'local>(mut env: JNIEnv<'local>, target: JObject<'local>, timeout_millis: jlong) -> jboolean {

    let event_result = get_event_co_object(&mut env, &target);

    if event_result.is_none() {
        // we just need to return any value, the exception will be handled above
        return JNI_FALSE;
    }

    let event = event_result.unwrap();

    // println!("awaitMillis(): event ptr={:p}", event);

    if timeout_millis == 0 {
        // this cannot timeout so we can ignore the result
        event.wait(Timeout::Infinite).unwrap();
        return JNI_TRUE;
    } else {
        // a failed result is returned if it timed out
        let result = event.wait(Timeout::Val(Duration::from_millis(timeout_millis as u64)));
        return match result {
            Ok(_) => JNI_TRUE,
            Err(_) => JNI_FALSE
        };
    }
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedCondition_signal<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) {

    let event_result = get_event_co_object(&mut env, &target);

    if event_result.is_none() {
        // we just need to return any value, the exception will be handled above
        return;
    }

    let event = event_result.unwrap();

    // println!("signal(): event ptr={:p}", event);

    event.set(EventState::Signaled).unwrap();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedCondition_clear<'local>(mut env: JNIEnv<'local>, target: JObject<'local>) {

    let event_result = get_event_co_object(&mut env, &target);

    if event_result.is_none() {
        // we just need to return any value, the exception will be handled above
        return;
    }

    let event = event_result.unwrap();

    // println!("clear(): event ptr={:p}", event);

    event.set(EventState::Clear).unwrap();
}