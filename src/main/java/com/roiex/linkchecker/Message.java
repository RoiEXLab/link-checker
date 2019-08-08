package com.roiex.linkchecker;

import java.nio.file.Path;

public class Message {
  private final boolean severe;
  private final String message;
  private final Path path;

  public Message(boolean severe, String message, Path path) {
    this.severe = severe;
    this.message = message;
    this.path = path;
  }

  public boolean isSevere() {
    return severe;
  }

  public String getMessage() {
    return message;
  }

  public Path getPath() {
    return path;
  }

  @Override
  public String toString() {
    return (severe ? "Error" : "Warning") + ": File '" + path.toAbsolutePath() + "' had issues: " + message;
  }
}
