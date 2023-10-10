use std::mem::ManuallyDrop;

pub struct FakeShmem {
    os_id: String,
    bytes: Vec<u8>
}

impl FakeShmem {

    pub fn create(size: usize) -> FakeShmem {
        FakeShmem {
            os_id: "/shmem_123".to_string(),
            bytes: Vec::with_capacity(size)
        }
    }

    pub fn get_os_id(&self) -> &str {
        return self.os_id.as_str();
    }

    pub fn as_ptr(&self) -> *const u8 {
        return self.bytes.as_ptr();
    }

}

impl Drop for FakeShmem {
    fn drop(&mut self) {
        println!("Dropping FakeSchema...");
    }
}

fn main() {
    println!("Running");

    let ptr = create();

    read(ptr);

    println!("Done");
}

fn read(ptr: usize) {
    // should NOT be zero
    println!("getOsId(): ptr was {}", ptr);

    println!("getOsId(): de-referencing ptr to shmem...");

    let shmem = unsafe { (ptr as *const ManuallyDrop<FakeShmem>).as_ref().unwrap() };

    println!("getOsId(): shmem ptr={:p}", shmem);

    println!("getOsId(): shmem byteptr={:p}", shmem.as_ptr());
    println!("getOsId(): shmem os_id={}", shmem.get_os_id());
}

fn create() -> usize {
    let shmem_raw = FakeShmem::create(4096);

    println!("create(): shmem_raw ptr={:p}", &shmem_raw);

    let shmem_boxed = ManuallyDrop::new(shmem_raw);

    println!("create(): shmem_boxed ptr={:p}", &shmem_boxed);

    let shmem = Box::new(shmem_boxed);

    println!("create(): shmem ptr={:p}", &shmem);

    println!("create(): shmem osid={}, byteptr={:p}", shmem.get_os_id(), shmem.as_ptr());

    let shmem_leaked = Box::leak(shmem);

    println!("create(): shmem_leaked ptr={:p}", shmem_leaked);

    let ptr = shmem_leaked as *const ManuallyDrop<FakeShmem> as usize;

    return ptr;

    // https://news.ycombinator.com/item?id=11880897
    // cast shmem to a pointer, then get the address of it
    // let ptr3: usize = &shmem as *const Box<ManuallyDrop<FakeShmem>> as usize;
    /*let ptr3 = Box::into_raw(shmem) as usize;
    println!("create(): ptr3 was {}", ptr3);

    let ptr: i64 = &shmem as *const Box<ManuallyDrop<FakeShmem>> as i64;

    println!("create(): ptr was {}", ptr);

    // holy crap, the primitive type of a long is a J!
    env.set_field(&shmem_jobj, "ptr", "J", JValue::Long(ptr))
        .unwrap();

    // now we'll take the ptr and get a shmem back?
    // https://stackoverflow.com/questions/66621363/can-you-cast-a-memory-address-as-a-usize-into-a-reference-with-a-lifetime
    unsafe {
        println!("create(): de-referencing ptr to shmem...");

        let ptr2 = ptr;
        let shmem2 = &*(ptr2 as *const Box<ManuallyDrop<FakeShmem>>);

        println!("create(): shmem2 osid={}, byteptr={:p}", shmem2.get_os_id(), shmem2.as_ptr());
    }

    return shmem_jobj.into_raw();*/
}