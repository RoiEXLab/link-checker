package com.roiex.linkchecker;

import java.net.MalformedURLException;
import java.util.function.BiConsumer;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;

public class RedirectListener extends LaxRedirectStrategy {

	private BiConsumer<String, String> action;

	public RedirectListener(BiConsumer<String, String> action) {
		super();
		this.action = action;
	}

	@Override
	public HttpUriRequest getRedirect(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
		HttpUriRequest newRequest = super.getRedirect(request, response, context);
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY) {
			if (request instanceof HttpUriRequest) {
				try {
					action.accept(((HttpUriRequest) request).getURI().toURL().toString(), newRequest.getURI().toURL().toString());
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
		}
		return newRequest;
	}
}
