package com.roiex.linkchecker;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class LinkProcessor {
  private String server;
  private Path base;

  public LinkProcessor(String server, File directory) {
    try {
      if (!server.endsWith("/")) {
        server += "/";
      }
      new URL(server);
      this.server = server;
      this.base = directory.toPath();
    } catch (IOException e) {
      System.err.println("Invalid URL: " + server);
      e.printStackTrace();
    }
  }

  List<Message> process() {
    try {
      return Files.find(base, 999, (path, bfa) -> bfa.isRegularFile() && path
          .getFileName().toString().matches(".*\\.(html|htm)"))
          .parallel()
          .map(this::processFile)
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Collections.emptyList();
  }

  private List<Message> processFile(Path path) {
    try (CloseableHttpClient client = HttpClients.custom()
        .setRetryHandler(new DefaultHttpRequestRetryHandler(1, false))
        .setRedirectStrategy(LinkChecker.ignore301() ? new LaxRedirectStrategy() : new RedirectLogger())
        .disableCookieManagement()
        .build()) {
      return getLinks(path)
          .map(link -> getFullLink(path, link))
          .flatMap(Optional::stream)
          .filter(link -> !LinkChecker.ignoreOutgoing() || !isOutgoing(link))
          .map(link -> processLink(client, link, path))
          .flatMap(Optional::stream)
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }


  private Stream<String> getLinks(Path path) {
    try {
      LinkChecker.logCurrent("Processing " + path.toAbsolutePath().normalize());
      Document doc = Jsoup.parse(path.toFile(), null);
      return Stream.of(
          extractLinks(doc.getElementsByTag("img"), "src"),
          extractLinks(doc.getElementsByTag("script"), "src"),
          extractLinks(doc.getElementsByTag("a"), "href"),
          extractLinks(doc.getElementsByTag("link"), "href")
      ).flatMap(Function.identity());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Stream<String> extractLinks(Elements elements, String attr) {
    return elements.stream().filter(e -> e.hasAttr(attr)).map(e -> e.attr(attr));
  }

  private Optional<String> getFullLink(Path source, String link) {
    if (!link.startsWith("#") && link.matches("(https?:)?//.*")) {
      if (link.startsWith("//")) {
        return Optional.of(server.split("//")[0] + link);
      }
      return Optional.of(link);
    } else if (!link.matches("^[a-zA-Z]+:.*")) {
      if (link.startsWith("/")) {
        return Optional.of(server + link);
      } else {
        return Optional.of(getAbsoluteURL(source, link));
      }
    }
    return Optional.empty();
  }

  private boolean isOutgoing(String link) {
    if (!link.startsWith("#") && link.matches("(https?:)?//.*")) {
      if (link.startsWith("//")) {
        link = server.split("//")[0] + link;
      }
      return !link.startsWith(server);
    }
    return false;
  }

  private String getAbsoluteURL(Path source, String link) {
    if (!source.toFile().isDirectory()) {
      source = source.toAbsolutePath().getParent();
    }
    Path relativePath = base.toAbsolutePath().relativize(source);
    try {
      return new URL(server + relativePath.toString().replaceAll("\\\\", "/") + "/" + link).toURI().normalize().toURL().toString();
    } catch (MalformedURLException | URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private Optional<Message> processLink(CloseableHttpClient client, String url, Path path) {
    HttpHead head = new HttpHead(url);
    LinkChecker.logCurrent("Checking " + url);
    var clientContext = new RedirectLogger.AdvancedHttpClientContext();

    try (CloseableHttpResponse response = client.execute(head, clientContext)) {
      int status = response.getStatusLine().getStatusCode();
      if (status / 100 != 2) {
        return Optional.of(new Message(isSevere(status), "Link '" + url + "' returned code " + status, path));
      }
      if (!clientContext.getPermanentHops().isEmpty()) {
        var entry = clientContext.getPermanentHops().entrySet().stream().findFirst().orElseThrow();
        return Optional.of(new Message(false, "Link '" + entry.getKey()
            + "' was redirected permanently to '" + entry.getValue() + "'. Consider updating this link", path));
      }
    } catch (IOException e) {
      return Optional.of(new Message(true, e.getMessage(), path));
    }
    return Optional.empty();
  }

  private boolean isSevere(int status) {
    if (status / 100 == 3) {
      throw new IllegalStateException("This request should have been redirected!");
    }
    return status / 100 != 5 || !LinkChecker.is500Allowed();
  }
}
