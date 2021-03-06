package com.roiex.linkchecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class LinkChecker {

  private static boolean is500allowed;
  private static boolean ignore301;
  private static boolean ignoreOutgoing;
  private static List<Pattern> regex = new ArrayList<>();

  public static void main(String[] args) throws ParseException, IOException {
    disableApacheLogging();
    Options options = new Options();
    options.addOption("s", "server", true, "Server to check example: http://localhost:80");
    options.addOption("d", "dir", true, "Directory with html files to check");
    options.addOption("if", "ignore-based-on-file", true, "(Optional) File which contains line-break-separated regular expressions. All errors/warnings matching those expressions will be ignored");
    options.addOption("l", "local-checks-only", false, "(Optional) If set, outgoing links will be ignored");
    options.addOption("i", "ignore-errors", false, "(Optional) If set, the exit code will always be 0");
    options.addOption("f5", "fail-500", false, "(Optional) If set, the application fails on 5XX status codes");
    options.addOption("i301", "ignore-301", false, "(Optional) If set, the application ignores 301 status codes");
    CommandLine cmd = new DefaultParser().parse(options, args);
    boolean ignoreErrors = cmd.hasOption('i');
    is500allowed = !cmd.hasOption("f5");
    ignore301 = cmd.hasOption("i301");
    ignoreOutgoing = cmd.hasOption("l");
    if (!cmd.hasOption("s") || !cmd.hasOption("d")) {
      new HelpFormatter().printHelp("LinkChecker", options);
      System.exit(-1);
    }
    if (cmd.hasOption("if")) {
      regex.addAll(Files.lines(Paths.get(cmd.getOptionValue("if")), StandardCharsets.UTF_8)
          .map(Pattern::compile)
          .collect(Collectors.toList()));
    }
    var messages = new LinkProcessor(cmd.getOptionValue('s'), Paths.get(cmd.getOptionValue('d'))).process();
    System.out.println();
    System.out.println();
    var filteredMessages = messages.stream()
        .sorted(Comparator
            .comparing(Message::isSevere)
            .reversed()
            .thenComparing(Message::getPath)
            .thenComparing(Message::getMessage))
        .map(Object::toString)
        .filter(message -> regex.stream().noneMatch(p -> p.matcher(message).matches()))
        .map(str -> str + '\n')
        .collect(Collectors.toList());

    filteredMessages.forEach(System.out::println);

    if (filteredMessages.isEmpty()) {
      System.out.println("Congratulations! No errors or warnings!");
    }
    if (!ignoreErrors && filteredMessages.stream().anyMatch(m -> m.startsWith("Error:"))) {
      System.exit(1);
    }
  }

  static boolean is500Allowed() {
    return is500allowed;
  }

  static boolean ignore301() {
    return ignore301;
  }
  static boolean ignoreOutgoing() {
    return ignoreOutgoing;
  }

  private static int oldLength = 0;
  static void logCurrent(String message) {
    int newLength = message.length();
    String newMessage = message
        + " ".repeat(Math.max(0, oldLength - newLength))
        + '\r';
    oldLength = newLength;
    System.out.print(newMessage);
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
