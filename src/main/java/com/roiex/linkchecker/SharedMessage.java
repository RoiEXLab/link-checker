package com.roiex.linkchecker;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SharedMessage {
	private final String message;
	private final Set<Path> affectedFiles;

	public SharedMessage(String message, Path... paths) {
		this(message, Arrays.asList(paths));
	}
	public SharedMessage(String message, List<Path> paths) {
		this(message, new HashSet<>(paths));
	}
	public SharedMessage(String message, Set<Path> paths) {
		this.message = message;
		affectedFiles = paths;
	}
	public String getMessage() {
		return message;
	}
	public Set<Path> getAffectedFiles() {
		return affectedFiles;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(message).append("\n\tAffected Files:\n");
		for (Path file : affectedFiles) {
			builder.append("\t\t").append(file.toAbsolutePath().toString()).append('\n');
		}
		return builder.toString();
	}
}
