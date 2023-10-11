headers:
	javac -h target/headers/ -d target/temp-classes/ src/main/java/com/fizzed/shmemj/*.java
	#$(JAVA_HOME)/bin/javah -cp src/main/java com.fizzed.siamese.SharedMemoryBuilder

clean:
	cd native && cargo clean
	mvn clean

lib:
	cd native && cargo build