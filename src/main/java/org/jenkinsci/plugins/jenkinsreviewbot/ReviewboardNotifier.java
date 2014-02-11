/*
Copyright (c) 2013 VMware, Inc. All Rights Reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to
deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
IN THE SOFTWARE.
*/

package org.jenkinsci.plugins.jenkinsreviewbot;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: ymeymann
 * Date: 6/3/13 10:09 PM
 */
public class ReviewboardNotifier extends Notifier implements MatrixAggregatable {

  /**
   * Maximum number of log lines to fetch from the Jenkins project
   */
  private static final int MAX_NUM_LOG_LINES = 100000;
  private final boolean shipItOnSuccess;
  private final String excerptRegex;
  private final String excerptLinesBefore;
  private final String excerptLinesAfter;
  private final String excerptDefaultLines;

  @DataBoundConstructor
  public ReviewboardNotifier(boolean shipItOnSuccess, String excerptRegex, String excerptLinesBefore, String excerptLinesAfter, String excerptDefaultLines) {
    this.shipItOnSuccess = shipItOnSuccess;
    this.excerptRegex = excerptRegex;
    this.excerptLinesBefore = excerptLinesBefore;
    this.excerptLinesAfter = excerptLinesAfter;
    this.excerptDefaultLines = excerptDefaultLines;
  }

  public boolean getShipItOnSuccess() {
    return shipItOnSuccess;
  }

  public String getExcerptRegex() {
    return excerptRegex;
  }

  public String getExcerptLinesBefore() {
    return excerptLinesBefore;
  }

  public String getExcerptLinesAfter() {
    return excerptLinesAfter;
  }

  public String getExcerptDefaultLines() {
    return excerptDefaultLines;
  }

  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.STEP;
  }

  public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
    return new MatrixAggregator(build, launcher, listener) {
      @Override
      public boolean endBuild() throws InterruptedException, IOException {
        return notifyReviewboard(listener, this.build);
      }
    };
  }

  private boolean notifyReviewboard(BuildListener listener, AbstractBuild<?, ?> build) {
    listener.getLogger().println("Going to notify reviewboard about " + build.getDisplayName());
    ParametersAction paramAction = build.getAction(ParametersAction.class);
    ReviewboardParameterValue rbParam = (ReviewboardParameterValue)paramAction.getParameter("review.url");
    String url = rbParam.getLocation();
    Result result = build.getResult();
    try {
      String link = build.getEnvironment(listener).get("BUILD_URL");
      String logExcerpt = getLogExcerpt(build);
      boolean patchFailed = rbParam.isPatchFailed();
      boolean success = result.equals(Result.SUCCESS);
      boolean unstable = result.equals(Result.UNSTABLE);
      String msg = patchFailed ? Messages.ReviewboardNotifier_PatchError() + "\n" + logExcerpt :
                   success     ? Messages.ReviewboardNotifier_BuildSuccess() + " " + link :
                   unstable    ? Messages.ReviewboardNotifier_BuildUnstable() + " " + link + "\n" + logExcerpt :
                                 Messages.ReviewboardNotifier_BuildFailure() + " " + link + "\n" + logExcerpt;

      rbParam.getConnection().postComment(url, build.getProject().getName()+": "+msg, success && getShipItOnSuccess());
    } catch (Exception e) {
      listener.getLogger().println("Error posting to reviewboard: " + e.toString());
    }
    return true;
  }

  private String getLogExcerpt(AbstractBuild build) throws Exception {
    List<String> logs = (List<String>)build.getLog(MAX_NUM_LOG_LINES);
    if (logs == null) {
      logs = new ArrayList<String>();
    }
    return LogFormatterUtil.getLogExcerpt(logs, excerptRegex,
        Integer.parseInt(excerptLinesBefore), Integer.parseInt(excerptLinesAfter), Integer.parseInt(excerptDefaultLines));
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    return notifyReviewboard(listener, build);
  }

  @Override
  public boolean needsToRunAfterFinalized() {
    return true;
  }

  @Override
  public ReviewboardDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  @Extension
  public static final ReviewboardDescriptor DESCRIPTOR = new ReviewboardDescriptor();

}
