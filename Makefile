headers:
	javac -h target/headers/ -d target/temp-classes/ src/main/java/com/fizzed/shmemj/*.java

clean:
	cd native && cargo clean
	mvn clean

lib:
	cd native && cargo build