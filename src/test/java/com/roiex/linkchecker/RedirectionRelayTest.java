package com.roiex.linkchecker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;

public class RedirectionRelayTest {

	@RepeatedTest(10)
	public void testCorrectSorting() {
		String baseURL = "http://example.com/";
		int size = 400;
		String[] urls = new String[size];
		for (int y = 0; y < size; y++) {
			urls[y] = baseURL + y;
		}
		LinkedList<RedirectionRelay> relays = new LinkedList<>(redirectionLinker(urls));
		//relays.addAll(redirectionLinker("https://google.com", "https://gmail.google.com", "https://play.google.com"));
		List<RedirectionRelay> copy = new ArrayList<>(relays);
		Collections.shuffle(relays, new Random(System.nanoTime()));

		LinkedList<RedirectionRelay> result = new LinkedList<>();
		while (!relays.isEmpty()) {
			RedirectionRelay newRelay = relays.poll();
			result.addFirst(newRelay);
			new LinkProcessor("http://localhost", new File("./")).addNextToList(newRelay, result, relays);
		}
//		while (result.getFirst().getSourceURL().contains("google")) {
//			result.addLast(result.pollFirst());
//		}
		Assertions.assertEquals(copy, result);
	}

	private List<RedirectionRelay> redirectionLinker(String... urls) {
		List<RedirectionRelay> result = new ArrayList<>();
		if (urls == null || urls.length < 2) {
			return result;
		}
		String oldURL = urls[0];
		for (int i = 1; i < urls.length; i++) {
			String newURL = urls[i];
			result.add(new RedirectionRelay(oldURL, newURL));
			oldURL = newURL;
		}
		return result;
	}
}
