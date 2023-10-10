use std::mem::ManuallyDrop;
use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JValue};
use jni::signature::JavaType;
use jni::sys::{_jobject, jstring, jlong};
use jni::sys::jobject;
use shared_memory::{Shmem, ShmemConf};

#[no_mangle]
pub extern "system" fn Java_com_fizzed_siamese_SharedMemoryFactory_create<'local>(
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
pub extern "system" fn Java_com_fizzed_siamese_SharedMemoryFactory_open<'local>(
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
    let shmem_boxed = ManuallyDrop::new(shmem);

    // println!("create(): shmem_boxed ptr={:p}", &shmem_boxed);

    // this moves it to the heap, but then leaks it back so we can keep it around
    let shmem = Box::leak(Box::new(shmem_boxed));

    println!("create(): shmem ptr={:p}, osid={}, byteptr={:p}", shmem, shmem.get_os_id(), shmem.as_ptr());

    let shared_memory_class = env.find_class("com/fizzed/siamese/SharedMemory").unwrap();

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

fn get_shmem_co_object<'local>(env: &mut JNIEnv, target: &JObject) -> &'local ManuallyDrop<Shmem> {
    // the "ptr" field on the SharedMemory class instance is the address of the companion object in rust
    let ptr = env.get_field(&target, "ptr", "J")
        .unwrap()
        .j()
        .unwrap();

    // NOTE: ptr should NOT be zero

    let shmem = unsafe { &*(ptr as *const ManuallyDrop<Shmem>) };

    return shmem;
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_siamese_SharedMemory_getOsId<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) -> jstring {

    let shmem = get_shmem_co_object(&mut env, &target);

    let os_id = shmem.get_os_id();

    let output = env.new_string(os_id)
        .expect("Couldn't create java string!");

    return output.into_raw();
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_siamese_SharedMemory_getSize<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) -> jlong {

    let shmem = get_shmem_co_object(&mut env, &target);

    let size = shmem.len();

    return size as jlong;
}

#[no_mangle]
pub extern "system" fn Java_com_fizzed_siamese_SharedMemory_getByteBuffer<'local>(
        mut env: JNIEnv<'local>, target: JObject<'local>) -> jobject {

    let shmem = get_shmem_co_object(&mut env, &target);

    let byte_buffer = unsafe { env.new_direct_byte_buffer(shmem.as_ptr(), shmem.len()).unwrap() };

    return byte_buffer.into_raw();
}