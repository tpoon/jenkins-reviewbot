package org.jenkinsci.plugins.jenkinsreviewbot;

import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogFormatterUtilTest {
  @Test
  public void testGetLogExcerpt() {
    String input = "Line 1\n" +
        "Line 2\n" +
        "Line 3\n" +
        "Line 4 error\n" +
        "Line 5\n" +
        "Line 6\n" +
        "Line 7\n" +
        "Line 8\n" +
        "Line 9";
    List<String> logs = Arrays.asList(input.split("\n"));

    // Test single match
    Assert.assertEquals("Line 2\n" +
        "Line 3\n" +
        "Line 4 error\n" +
        "Line 5\n" +
        "Line 6\n" +
        "Line 7", LogFormatterUtil.getLogExcerpt(logs, "(?i)\\berror\\b", 2, 3, 4));

    Assert.assertEquals("Line 2\n" +
        "Line 3\n" +
        "Line 4 error\n" +
        "Line 5\n" +
        "Line 6\n" +
        "Line 7", LogFormatterUtil.getLogExcerpt(logs, "(?i)\\b(cannot|error|exception|fatal|fail(ed|ure)|un(defined|resolved))\\b", 2, 3, 4));

    // Test no matches
    Assert.assertEquals("Line 6\n" +
        "Line 7\n" +
        "Line 8\n" +
        "Line 9", LogFormatterUtil.getLogExcerpt(logs, "No match", 2, 3, 4));

    // Test empty regex
    Assert.assertEquals("Line 6\n" +
        "Line 7\n" +
        "Line 8\n" +
        "Line 9", LogFormatterUtil.getLogExcerpt(logs, "", 2, 3, 4));

    // Test null regex
    Assert.assertEquals("Line 6\n" +
        "Line 7\n" +
        "Line 8\n" +
        "Line 9", LogFormatterUtil.getLogExcerpt(logs, null, 2, 3, 4));

    // Test first line match
    Assert.assertEquals("Line 1\n" +
        "Line 2\n" +
        "Line 3\n" +
        "Line 4 error", LogFormatterUtil.getLogExcerpt(logs, "Line 1", 2, 3, 4));

    // Test last line match
    Assert.assertEquals("Line 7\n" +
        "Line 8\n" +
        "Line 9", LogFormatterUtil.getLogExcerpt(logs, "Line 9", 2, 3, 4));
  }

  @Test
  public void testGetLogExcerptMulti() {
    String input = "Line 1\n" +
        "Line 2\n" +
        "Line 3\n" +
        "Line 4 undefined\n" +
        "Line 5\n" +
        "Line 6\n" +
        "Line 7 error\n" +
        "Line 8\n" +
        "Line 9\n" +
        "Line 10\n" +
        "Line 11\n" +
        "Line 12\n" +
        "Line 13\n" +
        "Line 14\n" +
        "Line 15 failure\n" +
        "Line 16 error\n" +
        "Line 17";
    List<String> logs = Arrays.asList(input.split("\n"));

    // Test multiple blocks of matches
    Assert.assertEquals("Line 2\n" +
        "Line 3\n" +
        "Line 4 undefined\n" +
        "Line 5\n" +
        "Line 6\n" +
        "Line 7 error\n" +
        "Line 8\n" +
        "Line 9\n" +
        "Line 10\n" +
        "...\n" +
        "Line 13\n" +
        "Line 14\n" +
        "Line 15 failure\n" +
        "Line 16 error\n" +
        "Line 17", LogFormatterUtil.getLogExcerpt(logs, "(?i)\\b(cannot|error|exception|fatal|fail(ed|ure)|un(defined|resolved))\\b", 2, 3, 4));
  }

}
