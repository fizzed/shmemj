use std::mem::ManuallyDrop;
use jni::JNIEnv;
use jni::objects::{JObject, JString, JValue};
use jni::sys::{jstring, jlong};
use jni::sys::jobject;
use raw_sync::events::{Event, EventImpl, EventInit};
use shared_memory::{Shmem, ShmemConf};

//
// SharedMemoryFactory native methods
//

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemoryFactory_create<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) -> jobject {

    let size = env.get_field(&target, "size", "J")
        .unwrap()
        .j()
        .unwrap();

    let shmem = ShmemConf::new()
        .size(size as usize)
        .create()
        .unwrap();

    return create_shmem_object(&mut env, shmem);
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemoryFactory_open<'local>(
    mut env: JNIEnv<'local>, target: JObject<'local>) -> jobject {

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

    println!("create(): size={}, os_id={}", size, os_id);

    let shmem_raw = ShmemConf::new()
        .size(size as usize)
        .os_id(os_id)
        .open()
        .unwrap();

    return create_shmem_object(&mut env, shmem_raw);
}

fn create_shmem_object(env: &mut JNIEnv, shmem: Shmem) -> jobject {
    let shmem_manually_dropped = ManuallyDrop::new(shmem);

    // println!("create(): shmem_boxed ptr={:p}", &shmem_boxed);

    // this moves it to the heap, but then leaks it back so we can keep it around
    let shmem = Box::leak(Box::new(shmem_manually_dropped));

    println!("create(): shmem ptr={:p}, osid={}, byteptr={:p}", shmem, shmem.get_os_id(), shmem.as_ptr());

    let shared_memory_class = env.find_class("com/fizzed/shmemj/SharedMemory").unwrap();

    //println!("Found shmem class: {:?}", shared_memory_class);

    let shmem_jobj = env.new_object(&shared_memory_class, "()V", &[])
        .unwrap();

    let ptr = shmem as *const ManuallyDrop<Shmem> as jlong;

    println!("create(): ptr was {}", ptr);

    // primitive type of a long is a J
    env.set_field(&shmem_jobj, "ptr", "J", JValue::Long(ptr))
        .unwrap();

    return shmem_jobj.into_raw();
}

//
// SharedMemory native methods
//

fn get_shmem_co_object<'local>(env: &mut JNIEnv, target: &JObject) -> &'local mut ManuallyDrop<Shmem> {
    // the "ptr" field on the SharedMemory class instance is the address of the companion object in rust
    let ptr = env.get_field(&target, "ptr", "J")
        .unwrap()
        .j()
        .unwrap();

    // NOTE: ptr should NOT be zero

    let shmem = unsafe { &mut *(ptr as *mut ManuallyDrop<Shmem>) };

    return shmem;
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_getOsId<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) -> jstring {

    let shmem = get_shmem_co_object(&mut env, &target);

    let os_id = shmem.get_os_id();

    let output = env.new_string(os_id)
        .expect("Couldn't create java string!");

    return output.into_raw();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_getSize<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) -> jlong {

    let shmem = get_shmem_co_object(&mut env, &target);

    let size = shmem.len();

    return size as jlong;
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_getByteBuffer<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) -> jobject {

    let shmem = get_shmem_co_object(&mut env, &target);

    let byte_buffer = unsafe { env.new_direct_byte_buffer(shmem.as_ptr(), shmem.len()).unwrap() };

    return byte_buffer.into_raw();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_destroy<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) {

    // do we need to destroy this?
    let ptr = env.get_field(&target, "ptr", "J")
        .unwrap()
        .j()
        .unwrap();

    if ptr == 0 {
        //println!("Shmem was already dropped");
        return;     // nothing to do
    }

    let shmem = get_shmem_co_object(&mut env, &target);

    unsafe {
        //println!("Dropping shmem...");
        ManuallyDrop::drop(shmem);
    }

    // clear out pointer
    env.set_field(&target, "ptr", "J", JValue::Long(0))
        .unwrap();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_shmemj_SharedMemory_newCondition<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>, offset: jlong) -> jobject {

    let shmem = get_shmem_co_object(&mut env, &target);

    //
    // create an "Event" that we'll associate with a SharedCondition
    //
    //

    let mem_ptr = unsafe { shmem.as_ptr().offset(offset as isize) };

    let (event_boxed, event_size) = unsafe { Event::new(mem_ptr, true).unwrap() };

    // since its already boxed, we'll leak it out, then make it manually dropped
    let event = Box::leak(event_boxed);

    println!("newCondition(): event ptr={:p}, size={}", event, event_size);

    let shared_condition_class = env.find_class("com/fizzed/shmemj/SharedCondition").unwrap();

    let shcond_obj = env.new_object(&shared_condition_class, "()V", &[])
        .unwrap();

    let ptr = event as * dyn EventImpl as usize;

    println!("newCondition(): ptr was {}", ptr);

    // primitive type of a long is a J
    env.set_field(&shcond_obj, "ptr", "J", JValue::Long(ptr as jlong))
        .unwrap();

    env.set_field(&shcond_obj, "size", "J", JValue::Long(event_size as jlong))
        .unwrap();

    return shcond_obj.into_raw();
}