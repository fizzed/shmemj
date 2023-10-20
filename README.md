# Shmemj (Shared Memory for Java)

# Building

You need to install the target for rust to compile with.  On windows:

    rustup target add x86_64-pc-windows-msvc
    rustup target add i686-pc-windows-msvc
    rustup target add aarch64-pc-windows-msvc

On macos:

    rustup target add x86_64-apple-darwin
    rustup target add aarch64-apple-darwin

On linux:

    rustup target add x86_64-unknown-linux-gnu
    rustup target add aarch64-unknown-linux-gnu