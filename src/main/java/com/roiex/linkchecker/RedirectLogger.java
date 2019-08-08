package com.roiex.linkchecker;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;

public class RedirectLogger extends LaxRedirectStrategy {

  static class AdvancedHttpClientContext extends HttpClientContext {
    private final Map<String, String> permanentHops = new HashMap<>();

    void addPermanentHop(String source, String target) {
      permanentHops.put(source, target);
    }

    Map<String, String> getPermanentHops() {
      return Collections.unmodifiableMap(permanentHops);
    }
  }

  @Override
  public HttpUriRequest getRedirect(
      final HttpRequest request,
      final HttpResponse response,
      final HttpContext context) throws ProtocolException {
    var result = super.getRedirect(request, response, context);
    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY
        && context instanceof AdvancedHttpClientContext
        && request instanceof HttpUriRequest) {
      try {
        var source = ((HttpUriRequest)request).getURI().toURL().toString();
        var target = result.getURI().toURL().toString();
        ((AdvancedHttpClientContext)context).addPermanentHop(source, target);
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }
}
