package com.roiex.linkchecker;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class RedirectionRelay implements Comparable<RedirectionRelay> {

	private String sourceURL;
	private String destinationURL;

	public RedirectionRelay(String sourceURL, String destinationURL) {
		this.sourceURL = sourceURL;
		this.destinationURL = destinationURL;
	}

	public String getSourceURL() {
		return sourceURL;
	}

	public String getDestinationURL() {
		return destinationURL;
	}

	@Override
	public int compareTo(RedirectionRelay arg0) {
		try {
			if (new URL(sourceURL).toURI().normalize().toURL().sameFile(new URL(arg0.destinationURL).toURI().normalize().toURL())) {
				return 1;
			} else if (new URL(destinationURL).toURI().normalize().toURL().sameFile(new URL(arg0.sourceURL).toURI().normalize().toURL())) {
				return -1;
			}
		} catch (MalformedURLException | URISyntaxException e) {
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ": " + sourceURL + " -> " + destinationURL;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof RedirectionRelay) {
			RedirectionRelay other = (RedirectionRelay) o;
			return sourceURL.equalsIgnoreCase(other.sourceURL) && destinationURL.equalsIgnoreCase(other.destinationURL);
		}
		return false;
	}
}
