package com.roiex.linkchecker;

import java.nio.file.Path;

public class URLSource {
	private final String url;
	private final Path sourceFile;

	public URLSource(String url, Path sourceFile) {
		this.url = url;
		this.sourceFile = sourceFile;
	}

	public String getURL() {
		return url;
	}

	public Path getPath() {
		return sourceFile;
	}

}
