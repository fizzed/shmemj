package com.fizzed.shmemj;

import com.fizzed.jne.JNE;
import com.fizzed.jne.MemoizedRunner;

/**
 * Custom double-locked safe loading of native libs.
 */
public class LibraryLoader {
  static private final MemoizedRunner LOADER = new MemoizedRunner();

  static public void loadLibrary() {
    LOADER.once(() -> {
      JNE.loadLibrary("shmemj");
    });
  }
}