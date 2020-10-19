/**
 * Copyright 2013 Cloudera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kitesdk.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Package an application on the local filesystem.
 */
@Mojo(name = "package-app", defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.RUNTIME)
public class PackageAppMojo extends AbstractAppMojo {

  /**
   * The tool class to run. The specified class must have a standard Java
   * <code>main</code> method.
   */
  @Parameter(property = "kite.toolClass", required = true)
  private String toolClass;

  /**
   * Arguments to pass to the tool, in addition to those generated by
   * <code>addDependenciesToDistributedCache</code> and <code>hadoopConfiguration</code>.
   */
  @Parameter(property = "kite.args")
  private String[] args;

  /**
   * Whether to add dependencies in the <i>runtime</i> classpath to Hadoop's distributed
   * cache so that they are added to the classpath for MapReduce tasks
   * (via <code>-libjars</code>).
   */
  @Parameter(property = "kite.addDependenciesToDistributedCache",
      defaultValue = "true")
  private boolean addDependenciesToDistributedCache;

  /**
   * Hadoop configuration properties.
   */
  @Parameter(property = "kite.hadoopConfiguration")
  private Properties hadoopConfiguration;

  /**
   * The type of the application (<code>workflow</code>, <code>coordination</code>,
   * or <code>bundle</code>).
   */
  // TODO: support applications which are more than one type
  @Parameter(property = "kite.applicationType",
      defaultValue = "workflow")
  private String applicationType;

  /**
   * Whether the workflow.xml should be generated or not.
   */
  @Parameter(property = "kite.generateWorkflowXml", defaultValue = "true")
  private boolean generateWorkflowXml = true;

  /**
   * Character encoding for the auto-generated workflow file.
   */
  @Parameter(property = "kite.workflowFileEncoding", defaultValue = "UTF-8")
  private String encoding;

  /**
   * The version of the Oozie workflow schema.
   */
  @Parameter(property = "kite.schemaVersion", defaultValue = "0.4")
  private String schemaVersion;

  /**
   * The name of the workflow.
   */
  @Parameter(property = "kite.workflowName", defaultValue = "${project.build.finalName}")
  private String workflowName;

  /**
   * The coordinator.xml file to use (only for applications of type
   * <code>coordinator</code>).
   */
  @Parameter(property = "kite.coordinatorFile",
      defaultValue = "${basedir}/src/main/oozie/coordinator.xml")
  private File coordinatorFile;

  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      String buildDirectory = mavenProject.getBuild().getDirectory();
      FileUtils.copyInputStreamToFile(
          getClass().getClassLoader().getResourceAsStream("assembly/oozie-app.xml"),
          new File(buildDirectory + "/assembly/oozie-app.xml"));
    } catch (IOException e) {
      throw new MojoExecutionException("Error copying assembly", e);
    }
    executeMojo(
        plugin(groupId("org.apache.maven.plugins"),
            artifactId("maven-assembly-plugin"),
            version("2.3")),
        goal("single"),
        configuration(element("descriptors",
                              element("descriptor", "${project.build.directory}/assembly/oozie-app.xml")),
                      element("finalName", applicationName),
                      element("appendAssemblyId", "false")),
        executionEnvironment(mavenProject, mavenSession, pluginManager));

    if (generateWorkflowXml) {
      File outputDir = new File(mavenProject.getBuild().getDirectory(), applicationName);
      File workflowXml = new File(outputDir, "workflow.xml");

      String hadoopFs = hadoopConfiguration.getProperty("fs.default.name", "${"+AbstractAppMojo.NAMENODE_PROPERTY+"}");
      String hadoopJobTracker = hadoopConfiguration.getProperty("mapred.job.tracker", "${"+AbstractAppMojo.JOBTRACKER_PROPERTY+"}");

      List<Path> libJars = new ArrayList<Path>();
      if (addDependenciesToDistributedCache) {
        File lib = new File(outputDir, "lib");
        Collection<File> deps = FileUtils.listFiles(lib, new String[]{"jar"}, false);
        for (File dep : deps) {
          Path libJarPath = new Path(new Path(getAppPath(), "lib"), dep.getName());
          libJarPath = new Path(hadoopFs, libJarPath);
          libJars.add(libJarPath);
        }
      }
      Workflow workflow = new Workflow(workflowXml, schemaVersion, workflowName,
          toolClass, args, hadoopConfiguration, hadoopFs, hadoopJobTracker, libJars);
      WorkflowXmlWriter workflowXmlWriter = new WorkflowXmlWriter(encoding);
      workflowXmlWriter.write(workflow);
    }

    if ("coordinator".equals(applicationType)) {
      File outputDir = new File(mavenProject.getBuild().getDirectory(), applicationName);
      File coordinatorXml = new File(outputDir, "coordinator.xml");
      try {
        FileUtils.copyFile(coordinatorFile, coordinatorXml);
      } catch (IOException e) {
        throw new MojoExecutionException("Error copying coordinator file", e);
      }
    }
  }
}
