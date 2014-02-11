package org.jenkinsci.plugins.jenkinsreviewbot;

import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

public class LogFormatterUtil {

  /**
   * Returns an excerpt from the given logs for the lines that match the given regular expression.
   * If there are multiple matches, they are all included in the excerpt and separated with "..."
   * @param logs the logs
   * @param excerptRegex the regular expression to match
   * @param excerptLinesBefore the number of lines before each match to include in the excerpt
   * @param excerptLinesAfter the number of lines after each match to include in the excerpt
   * @param excerptDefaultLines the number of default lines (counted from the end) to include if there are no matches
   * @return the exceprt
   */
  public static String getLogExcerpt(List<String> logs, String excerptRegex, int excerptLinesBefore, int excerptLinesAfter, int excerptDefaultLines) {
    if (logs == null) {
      return "";
    }

    // Just return the last <excerptDefaultLines> lines if there's no regex or if there are no matches.
    if (StringUtils.isEmpty(excerptRegex)) {
      return StringUtils.join(logs.subList(Math.max(0, logs.size() - excerptDefaultLines), logs.size()), "\n");
    }

    Pattern pattern = Pattern.compile(excerptRegex);
    int match = findNextMatch(logs, pattern, 0);
    if (match < 0) {
      return StringUtils.join(logs.subList(Math.max(0, logs.size() - excerptDefaultLines), logs.size()), "\n");
    }

    StringBuilder res = new StringBuilder();
    while (match >= 0) {
      // Separate excerpt blocks with "...".
      if (res.length() > 0) {
        res.append("\n...");
      }

      // Identify the start and end of the excerpt block.
      int start = Math.max(0, match - excerptLinesBefore);
      int end = Math.min(logs.size(), match + 1 + excerptLinesAfter);

      // See if the next match(es) overlap the current block. If so, include them in this block.
      int nextMatch = findNextMatch(logs, pattern, match + 1);
      while (nextMatch > 0 && (nextMatch - excerptLinesBefore <= end)) {
        match = nextMatch;
        end = Math.min(logs.size(), match + 1 + excerptLinesAfter);
        nextMatch = findNextMatch(logs, pattern, match + 1);
      }

      // Add the block to the result.
      for (int i=start; i<end; i++) {
        if (res.length() > 0) {
          res.append('\n');
        }
        res.append(logs.get(i));
      }
      match = findNextMatch(logs, pattern, end);
    }
    return res.toString();
  }

  protected static int findNextMatch(List<String> logs, Pattern pattern, int start) {
    for (int i=start; i<logs.size(); i++) {
      if (pattern.matcher(logs.get(i)).find()) {
        return i;
      }
    }
    return -1;
  }
}
