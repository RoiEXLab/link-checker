package com.roiex.linkchecker;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class LinkProcessor {

	private Queue<URLSource> outgoingLinks = new LinkedList<>();
	private Queue<URLSource> localLinks = new LinkedList<>();
	private String server;
	private Map<URL, Set<Path>> checkedLinks = new HashMap<>();
	private Path base;

	public LinkProcessor(String server, File directory) {
		try {
			if (!server.endsWith("/")) {
				server += "/";
			}
			new URL(server);
			this.server = server;
			this.base = directory.toPath();
			try (CloseableHttpClient client = HttpClients.custom().setRetryHandler(new DefaultHttpRequestRetryHandler(1, false)).setRedirectStrategy(new LaxRedirectStrategy()).disableCookieManagement().build()) {
				Files.find(base, 999, (path, bfa) -> bfa.isRegularFile() && path.getFileName().toString().matches(".*\\.(html|htm)")).forEach(this::getLinks);
				System.out.println();
				processQueue(client);
			}
		} catch (IOException e) {
			System.err.println("Invalid URL: " + server);
			e.printStackTrace();
		}
	}
	private void getLinks(Path path) {
		try {
			LinkChecker.logCurrent("Processing " + path.toAbsolutePath());
			Document doc = Jsoup.parse(path.toFile(), null);
			extractAttributes(path, doc.getElementsByTag("img"), "src");
			extractAttributes(path, doc.getElementsByTag("script"), "src");
			extractAttributes(path, doc.getElementsByTag("a"), "href");
			extractAttributes(path, doc.getElementsByTag("link"), "href");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void extractAttributes(Path source, Elements elements, String attr) {
		for (Element e : elements) {
			if (e.hasAttr(attr)) {
				String link = e.attr(attr);
				if (link.startsWith("#")) {
					continue;
				} else if (link.matches("https?://.*")) {
					if (link.startsWith(server)) {
						localLinks.add(new URLSource(link, source));
					} else {
						outgoingLinks.add(new URLSource(link, source));
					}
				} else if (!link.matches("^[a-zA-Z]+:.*")) {
					if (link.startsWith("/")) {
						localLinks.add(new URLSource(server + link, source));
					} else {
						localLinks.add(new URLSource(getAbsoluteURL(source, link), source));
					}
				}
			}
		}
	}

	private String getAbsoluteURL(Path source, String link) {
		if (!source.toFile().isDirectory()) {
			source = source.toAbsolutePath().getParent();
		}
		Path relativePath = base.toAbsolutePath().relativize(source);
		try {
			return new URL(server + relativePath.toString().replaceAll("\\\\", "/") + "/" + link).toString();
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void processQueue(CloseableHttpClient client) {
		try {
			processQueue(client, localLinks);
			if (!LinkChecker.ignoreOutgoing()) {
				processQueue(client, outgoingLinks);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void processQueue(CloseableHttpClient client, Queue<URLSource> queue) throws ClientProtocolException, IOException {
		System.out.println();
		while (!queue.isEmpty()) {
			URLSource urlSource = queue.poll();
			String url = urlSource.getURL();
			URL urlObject = new URL(url);
			if (checkedLinks.containsKey(urlObject)) {
				checkedLinks.get(urlObject).add(urlSource.getPath());
				continue;
			}
			checkedLinks.put(urlObject, new HashSet<>(Arrays.asList(urlSource.getPath())));
			try {
				HttpHead head = new HttpHead(url);
				LinkChecker.logCurrent("Checking " + url);
				try (CloseableHttpResponse response = client.execute(head)) {
					int status = response.getStatusLine().getStatusCode();
					int statusType = Integer.parseInt(String.valueOf(Integer.toString(status).charAt(0)));
					if (statusType != 2) {
						if (statusType == 3 && !LinkChecker.is300Allowed()) {
							LinkChecker.warn(new SharedMessage("Link '" + url + "' returned code " + status, checkedLinks.get(urlObject)));
						} else if (statusType == 5) {
							if (LinkChecker.is500Allowed()) {
								LinkChecker.warn(generateSharedStatusError(url, urlObject, status));
							} else {
								LinkChecker.fail(generateSharedStatusError(url, urlObject, status));
							}
						} else {
							LinkChecker.fail(generateSharedStatusError(url, urlObject, status));
						}
					}
				} catch (Exception e) {
					LinkChecker.fail(new SharedMessage("Error while trying to access " + url + e.getMessage(), checkedLinks.get(urlObject)));
				}
			} catch (Exception e) {
				LinkChecker.fail(new SharedMessage(e.getMessage(), checkedLinks.get(urlObject)));
			}
		}
	}

	private SharedMessage generateSharedStatusError(String url, URL urlObject, int status) {
		return new SharedMessage("Link '" + url + "' returned code " + status, checkedLinks.get(urlObject));
	}
}
