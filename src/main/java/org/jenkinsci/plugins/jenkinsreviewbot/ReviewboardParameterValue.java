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

import com.cloudbees.diff.ContextualPatch;
import com.cloudbees.diff.PatchException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.util.IOException2;
import hudson.util.VariableResolver;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.util.List;

/**
 * User: ymeymann
 * Date: 6/2/13 7:40 PM
 */
public class ReviewboardParameterValue extends ParameterValue {

  private final String url;
  private boolean patchFailed = false;
  private transient volatile ReviewboardConnection connection = null;

  @DataBoundConstructor
  public ReviewboardParameterValue(String name, String value) {
    super("review.url");
    url = buildReviewUrl(value);
  }

  public String getLocation() {
    return url;
  }

  @Override
  public String toString() {
    return "review.url='" + url + "'";
  }

  private static final String LOCATION = "patch.diff";

  @Override
  public BuildWrapper createBuildWrapper(AbstractBuild<?,?> build) {
    return new ReviewboardBuildWrapper() ;
  }

  private File getLocationUnderBuild(AbstractBuild build) {
    return new File(build.getRootDir(), "fileParameters/" + LOCATION);
  }

  public boolean isPatchFailed() {
    return patchFailed;
  }

  private void setPatchFailed(boolean patchFailed) {
    this.patchFailed = patchFailed;
  }

  // copied from PatchParameterValue
//  @Override
//  public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
//    // no environment variable
//  }

  // copied from PatchParameterValue
  @Override
  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
    return VariableResolver.NONE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ReviewboardParameterValue that = (ReviewboardParameterValue) o;

    if (url != null ? !url.equals(that.url) : that.url != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

//  private FileItem getDiffFile() {
//    File patchFile = null;
//    try {
//      File tempDir = new File(System.getProperty("java.io.tmpdir"));
//      patchFile = new File(tempDir, LOCATION);
//      String diff = getConnection().getDiffAsString(url);
//      savePatch(patchFile, diff);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    return new FileParameterValue.FileItemImpl(patchFile);
//  }

  synchronized ReviewboardConnection getConnection() {
    if (connection == null) {
      ReviewboardDescriptor d = ReviewboardNotifier.DESCRIPTOR;
      connection = new ReviewboardConnection(d.getReviewboardURL(),
          d.getReviewboardUsername(),
          d.getReviewboardPassword());
    }
    return connection;
  }

  private void savePatch(File patchFile, String diff) throws IOException {
    if (!patchFile.exists()) patchFile.createNewFile();
    Writer w = new BufferedWriter(new FileWriter(patchFile));
    w.write(diff);
    w.close();
  }

  private String buildReviewUrl(String value) {
    //if full url is given, just make sure iit ends with /
    //but if a number is given, construct the url from number based on configured Reviewboard home URL
    if (!value.startsWith("http")) {
      return getConnection().buildReviewUrl(value);
    } else {
      StringBuilder sb = new StringBuilder(value);
      if (sb.charAt(sb.length() - 1) != '/' ) sb.append('/');
      return sb.toString();
    }
  }

  private void applyPatch(BuildListener listener, FilePath patch) throws IOException, InterruptedException {
    listener.getLogger().println("Applying "+ ReviewboardNote.encodeTo("the diff"));
    try {
      patch.act(new ApplyTask(listener));
    } catch (IOException e) {
      listener.getLogger().println("Failed to apply patch due to:");
      e.printStackTrace(listener.getLogger());
      setPatchFailed(true);
      throw e;
    }
  }

//  copied from FileParameterValue
  @Override
  public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
    env.put("REVIEW_URL",url);
    String branch = "master";
    try {
      branch = getConnection().getBranch(url);
    } catch (IOException e) {
      e.printStackTrace();
    }
    env.put("REVIEW_BRANCH", branch);
  }

//  copied from FileParameterValue
//  @Override
//  public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
//    return new VariableResolver<String>() {
//      public String resolve(String name) {
//        return ReviewboardParameterValue.this.name.equals(name) ? url : null;
//      }
//    };
//  }

  class ReviewboardBuildWrapper extends BuildWrapper {
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
      if (!StringUtils.isEmpty(url)) {
        FilePath patch = build.getWorkspace().child(LOCATION);
        patch.delete();
        patch.getParent().mkdirs();
        patch.copyFrom(getConnection().getDiff(url)); //getDiffFile()
        patch.copyTo(new FilePath(getLocationUnderBuild(build)));
        if (patch.exists()) {
          applyPatch(listener, patch);
        }
      }
      return new BuildWrapper.Environment() {
        @Override
        public boolean tearDown( AbstractBuild build, BuildListener listener ) throws IOException, InterruptedException {
          if (connection != null) connection.close();
          return super.tearDown(build, listener);
        }
      };
    }
  }

  static class ApplyTask implements FilePath.FileCallable<Void> {
    private static final long serialVersionUID = 1L;
    private BuildListener listener;

    public ApplyTask(BuildListener listener) {
      this.listener = listener;
    }

    public Void invoke(File diff, VirtualChannel channel) throws IOException, InterruptedException {

      String[] patchCommandParts = new String[]{"patch", "-p1", "-f", "--verbose",
              "-d", diff.getParentFile().getAbsolutePath(),
              "-i", diff.getAbsolutePath()};

      listener.getLogger().println("Running command: "+StringUtils.join(patchCommandParts, " "));
      Process process = Runtime.getRuntime().exec(patchCommandParts);
      int exitValue = process.waitFor();
      String patchOutput = IOUtils.toString(process.getInputStream())+"\n\n"+IOUtils.toString(process.getErrorStream());
      listener.getLogger().println("Patch results:\n"+patchOutput);
      if (exitValue != 0) {
          throw new IOException("Failed to patch file (exit value "+exitValue+"): "+patchOutput);
      }

      return null;
    }
  }

}
