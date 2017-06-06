package com.roiex.linkchecker;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;

public class RedirectListener extends LaxRedirectStrategy {

	private Consumer<RedirectionRelay> action;
	private Set<RedirectionRelay> redirects = new HashSet<>();

	public RedirectListener(Consumer<RedirectionRelay> action) {
		super();
		this.action = action;
	}

	@Override
	public HttpUriRequest getRedirect(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
		HttpUriRequest newRequest = super.getRedirect(request, response, context);
		if (request instanceof HttpUriRequest) {
			try {
				String sourceURL = ((HttpUriRequest) request).getURI().normalize().toURL().toString();
				String destinationURL = newRequest.getURI().normalize().toURL().toString();
				RedirectionRelay relay = new RedirectionRelay(sourceURL, destinationURL);
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY) {
					action.accept(relay);
				}
				redirects.add(relay);
				LinkChecker.logCurrent("Checking " + destinationURL);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}

		}
		return newRequest;
	}

	public Set<RedirectionRelay> getRedirects() {
		return Collections.unmodifiableSet(redirects);
	}
}
