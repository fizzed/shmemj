headers:
	/usr/lib/jvm/zulu8.66.0.15-ca-jdk8.0.352-linux_x64/bin/javah -cp src/main/java com.fizzed.siamese.SharedMemory
	/usr/lib/jvm/zulu8.66.0.15-ca-jdk8.0.352-linux_x64/bin/javah -cp src/main/java com.fizzed.siamese.SharedMemoryBuilder

lib:
	cd native && cargo build