package com.roiex.linkchecker;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
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
		} catch (IOException e) {
			System.err.println("Invalid URL: " + server);
			e.printStackTrace();
		}
	}

	public void process() {
		RedirectListener permanentRedirHandler = new RedirectListener((r) -> {
			if (!LinkChecker.ignore301()) {
				try {
					URL url = new URL(r.getSourceURL()).toURI().normalize().toURL();
					if (!checkedLinks.containsKey(url)) {
						checkedLinks.put(url, new HashSet<>());
					}
					LinkChecker.warn(new SharedMessage("Link '" + url.toString() + "' was redirected permanently to '" + r.getDestinationURL() + "' Consider updating this link", checkedLinks.get(url)));
				} catch (MalformedURLException | URISyntaxException e) {
					e.printStackTrace();
				}
			}
		});
		try (CloseableHttpClient client = HttpClients.custom().setRetryHandler(new DefaultHttpRequestRetryHandler(1, false)).setRedirectStrategy(permanentRedirHandler).disableCookieManagement().build()) {
			Files.find(base, 999, (path, bfa) -> bfa.isRegularFile() && path.getFileName().toString().matches(".*\\.(html|htm)")).forEach(this::getLinks);
			System.out.println();
			processQueue(client);
		} catch (IOException e) {
			e.printStackTrace();
		}
		addRelayFiles(permanentRedirHandler.getRedirects());
	}

	void addRelayFiles(Set<RedirectionRelay> redirects) {
		try {
			LinkedList<RedirectionRelay> result = new LinkedList<>();
			LinkedList<RedirectionRelay> relays = new LinkedList<>(redirects);
			while (!relays.isEmpty()) {
				RedirectionRelay newRelay = relays.poll();
				result.addLast(newRelay);
				addNextToList(newRelay, result, relays);
			}
			for (RedirectionRelay redirect : result) {
				Set<Path> affectedFiles = checkedLinks.getOrDefault(new URL(redirect.getSourceURL()).toURI().normalize().toURL(), new HashSet<>());
				URL destURL = new URL(redirect.getDestinationURL()).toURI().normalize().toURL();
				if (!checkedLinks.containsKey(destURL)) {
					checkedLinks.put(destURL, new HashSet<>());
				}
				checkedLinks.get(destURL).addAll(affectedFiles);
			}
		} catch (MalformedURLException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	void addNextToList(RedirectionRelay start, LinkedList<RedirectionRelay> source, LinkedList<RedirectionRelay> pool) {
		RedirectionRelay prev = null;
		RedirectionRelay next = null;
		for (RedirectionRelay current : pool) {
			int compareValue = start.compareTo(current);
			if (compareValue < 0) {
				next = current;
			} else if (compareValue > 0) {
				prev = current;
			}
			if (prev != null && next != null) {
				break;
			}
		}
		if (prev != null) {
			pool.remove(prev);
			source.addFirst(prev);
			addNextToList(prev, source, pool);
		}
		if (next != null) {
			pool.remove(next);
			source.addLast(next);
			addNextToList(next, source, pool);
		}
	}

	private void getLinks(Path path) {
		try {
			LinkChecker.logCurrent("Processing " + path.toAbsolutePath().normalize());
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
				} else if (link.matches("(https?:)?//.*")) {
					if (link.startsWith("//")) {
						link = server.split("//")[0] + link;
					}
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
			return new URL(server + relativePath.toString().replaceAll("\\\\", "/") + "/" + link).toURI().normalize().toURL().toString();
		} catch (MalformedURLException | URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void processQueue(CloseableHttpClient client) {
		try {
			processQueue(client, localLinks);
			if (!LinkChecker.ignoreOutgoing()) {
				processQueue(client, outgoingLinks);
			}
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}
	private void processQueue(CloseableHttpClient client, Queue<URLSource> queue) throws IOException, URISyntaxException {
		System.out.println();
		while (!queue.isEmpty()) {
			URLSource urlSource = queue.poll();
			String url = urlSource.getURL();
			URL urlObject = new URL(url).toURI().normalize().toURL();
			if (checkedLinks.containsKey(urlObject)) {
				checkedLinks.get(urlObject).add(urlSource.getPath());
				continue;
			}
			checkedLinks.put(urlObject, new HashSet<>(Arrays.asList(urlSource.getPath().toAbsolutePath().normalize())));
			try {
				HttpHead head = new HttpHead(url);
				LinkChecker.logCurrent("Checking " + urlObject.toString());
				try (CloseableHttpResponse response = client.execute(head)) {
					int status = response.getStatusLine().getStatusCode();
					int statusType = Integer.parseInt(String.valueOf(Integer.toString(status).charAt(0)));
					if (statusType != 2) {
						if (statusType == 3) {
							throw new IllegalStateException("This request should have been redirected!");
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
					LinkChecker.fail(new SharedMessage("Error while trying to access '" + url + "' " + e.getMessage(), checkedLinks.get(urlObject)));
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
