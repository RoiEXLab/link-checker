package com.roiex.linkchecker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class LinkChecker {

	private static boolean ignoreErrors;
	private static boolean failed;
	private static boolean is500allowed;
	private static boolean is300allowed;
	private static boolean ignoreOutgoing;
	private static List<SharedMessage> errorMessages = new ArrayList<>();
	private static List<SharedMessage> warningMessages = new ArrayList<>();

	public static void main(String[] args) {
		disableApacheLogging();
		Options options = new Options();
		options.addOption("s", "server", true, "Server to check example: http://localhost:80");
		options.addOption("d", "dir", true, "Directory with html files to check");
		options.addOption("l", "local-checks-only", false, "(Optional) If set, outgoing links will be ignored");
		options.addOption("i", "ignore-errors", false, "(Optional) If set, the exit code will always be 0");
		options.addOption("f5", "fail-500", false, "(Optional) If set, the applications fails on 5XX exit codes");
		options.addOption("f3", "fail-300", false, "(Optional) If set, the applications fails on 3XX exit codes");
		try {
			CommandLine cmd = new DefaultParser().parse(options, args);
			ignoreErrors = cmd.hasOption('i');
			is500allowed = !cmd.hasOption("f5");
			is300allowed = !cmd.hasOption("f3");
			ignoreOutgoing = cmd.hasOption("l");
			if (!cmd.hasOption("s") || !cmd.hasOption("d")) {
				new HelpFormatter().printHelp("LinkChecker", options);
				System.exit(-1);
			}
			new LinkProcessor(cmd.getOptionValue('s'), new File(cmd.getOptionValue('d')));
			System.out.println();
			if (!errorMessages.isEmpty()) {
				System.out.println();
				System.out.println("Errors:");
			}
			for (SharedMessage message : errorMessages) {
				System.out.println();
				System.out.println(message);
			}
			if (!warningMessages.isEmpty()) {
				System.out.println();
				System.out.println("Warnings:");
			}
			for (SharedMessage message : warningMessages) {
				System.out.println();
				System.out.println(message);
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if (failed) {
			System.exit(1);
		}
	}

	public static void fail(SharedMessage message) {
		if (!ignoreErrors) {
			failed = true;
		}
		errorMessages.add(message);
	}

	public static void warn(SharedMessage message) {
		warningMessages.add(message);
	}

	public static boolean is500Allowed() {
		return is500allowed;
	}

	public static boolean is300Allowed() {
		return is300allowed;
	}
	public static boolean ignoreOutgoing() {
		return ignoreOutgoing;
	}

	private static int oldLength = 0;
	public static void logCurrent(String message) {
		int newLength = message.length();
		StringBuilder builder = new StringBuilder();
		builder.append(message);
		for (int i = newLength; i < oldLength; i++) {
			builder.append(' ');
		}
		builder.append('\r');
		oldLength = newLength;
		System.out.print(builder.toString());
	}

	private static void disableApacheLogging() {
		Logger.getLogger("org.apache.http.wire").setLevel(Level.FINEST);
		Logger.getLogger("org.apache.http.headers").setLevel(Level.FINEST);
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "ERROR");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "ERROR");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "ERROR");
	}
}
