package com.roiex.linkchecker;

import java.nio.file.Path;

public class Message {
  private final boolean severe;
  private final String message;
  private final Path path;

  Message(boolean severe, String message, Path path) {
    this.severe = severe;
    this.message = message;
    this.path = path;
  }

  boolean isSevere() {
    return severe;
  }

  String getMessage() {
    return message;
  }

  Path getPath() {
    return path;
  }

  @Override
  public String toString() {
    return (severe ? "Error" : "Warning") + ": File '" + path.toAbsolutePath() + "' had issues: " + message;
  }
}
