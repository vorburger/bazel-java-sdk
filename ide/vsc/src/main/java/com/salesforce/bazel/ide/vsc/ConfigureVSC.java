/**
 * Copyright (c) 2023, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.ide.vsc;

import com.salesforce.bazel.sdk.aspect.AspectDependencyGraphFactory;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.aspect.LocalBazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.console.StandardCommandConsoleFactory;
import com.salesforce.bazel.sdk.graph.BazelDependencyGraph;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspath;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.JvmUnionClasspath;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy.JvmClasspathAspectStrategy;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy.JvmClasspathStrategy;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.util.ImplicitClasspathHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageInfo;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolverImpl;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This CLI tool writes the configuration file of VSC's Java classpath for the
 * JDT-based redhat.java VSC extension from a Bazel dependency graph.
 */
public class ConfigureVSC {
    // where bazel is installed on your machine
    private static String bazelExecutablePath;
    private static File bazelExecutableFile;

    // the location of the Bazel workspace to analyze
    private static String bazelWorkspacePath;
    private static File bazelWorkspaceDir;

    // optional parameter to limit analysis to a particular package (and subdirs);
    // if not specified
    // this app will analyze your entire workspace, which will be painful if you
    // have more than a few hundred targets
    private static String rootPackageToAnalyze = null;
    private static Set<String> pathsToIgnore = null;

    // TODO Unpack this from classpath, à la extractFromClasspathToFile() from
    // https://github.com/vorburger/MariaDB4j/blob/main/mariaDB4j-core/src/main/java/ch/vorburger/mariadb4j/Util.java
    private static final String ASPECT_LOCATION = "/home/vorburger/git/github.com/salesforce/bazel-java-sdk/sdk/bazel-java-sdk/aspect";

    private static final String PACKAGE = "//ide/vsc";

    private static BazelWorkspaceScanner workspaceScanner = new BazelWorkspaceScanner();

    public static void main(String[] args) throws Exception {
        // TODO Remove hard-coded paths from initial testing
        mainORIGINAL(new String[] {
                "/home/vorburger/git/github.com/vorburger/vorburger-dotfiles-bin-etc/bin/bazel",
                "/home/vorburger/git/github.com/salesforce/bazel-java-sdk",
                PACKAGE });
    }

    public static void mainORIGINAL(String[] args) throws Exception {
        parseArgs(args);

        // The following code was originally inspired by the BazelAnalyzerApp example

        // Load the rules support, currently only JVM rules (java_library etc) are
        // supported
        JvmRuleInit.initialize();

        // load the aspect (the component we use to introspect the Bazel build) on the
        // file system
        File aspectDir = loadAspectDirectory(ASPECT_LOCATION);

        // set up the command line env
        BazelAspectLocation aspectLocation = new LocalBazelAspectLocation(aspectDir);
        CommandConsoleFactory consoleFactory = new StandardCommandConsoleFactory();
        CommandBuilder commandBuilder = new ShellCommandBuilder(consoleFactory);
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = new BazelWorkspaceCommandRunner(bazelExecutableFile,
                aspectLocation, commandBuilder, consoleFactory, bazelWorkspaceDir);

        // create the Bazel workspace SDK objects
        String workspaceName = BazelWorkspaceScanner.getBazelWorkspaceName(bazelWorkspacePath); // TODO use a File arg
        OperatingEnvironmentDetectionStrategy osDetector = new RealOperatingEnvironmentDetectionStrategy();
        BazelWorkspace bazelWorkspace = new BazelWorkspace(workspaceName, bazelWorkspaceDir, osDetector,
                bazelWorkspaceCmdRunner);
        BazelWorkspaceCommandOptions bazelOptions = bazelWorkspace.getBazelWorkspaceCommandOptions();
        printBazelOptions(bazelOptions);

        // scan for Bazel packages and print them out
        BazelPackageInfo rootPackage = workspaceScanner.getPackages(bazelWorkspaceDir, pathsToIgnore);
        List<BazelPackageLocation> selectedPackages = rootPackage.gatherChildren(rootPackageToAnalyze);
        for (BazelPackageLocation pkg : selectedPackages) {
            printPackage(pkg);
        }

        // run the Aspects to compute the dependency data
        AspectTargetInfos aspects = new AspectTargetInfos();
        Map<BazelLabel, Set<AspectTargetInfo>> aspectMap = bazelWorkspaceCmdRunner
                .getAspectTargetInfoForPackages(selectedPackages, "BazelAnalyzerApp");
        for (BazelLabel target : aspectMap.keySet()) {
            Set<AspectTargetInfo> aspectsForTarget = aspectMap.get(target);
            aspects.addAll(aspectsForTarget);
        }

        // use the dependency data to interact with the dependency graph (print root
        // labels)
        BazelDependencyGraph depGraph = AspectDependencyGraphFactory.build(aspects, false);
        Set<String> rootLabels = depGraph.getRootLabels();
        printRootLabels(rootLabels);

        // put them in the right order for analysis
        ProjectOrderResolver projectOrderResolver = new ProjectOrderResolverImpl();
        Iterable<BazelPackageLocation> orderedPackages = projectOrderResolver.computePackageOrder(rootPackage,
                selectedPackages, aspects);
        printPackageListOrder(orderedPackages);

        // NOW we figure out the classpath... (This section is new!)
        List<JvmClasspathStrategy> strategies = new ArrayList<>();
        BazelProjectManager bazelProjectManager = new SimpleBazelProjectManager();
        ImplicitClasspathHelper implicitDependencyHelper = new ImplicitClasspathHelper();
        BazelCommandManager bazelCommandManager = new BazelCommandManager(aspectLocation, commandBuilder,
                consoleFactory, bazelExecutableFile);
        strategies.add(new JvmClasspathAspectStrategy(bazelWorkspace, bazelProjectManager,
                implicitDependencyHelper, osDetector, bazelCommandManager));

        BazelProject bazelProject = new BazelProject(PACKAGE);
        bazelProject.getProjectStructure().bazelTargets.add(new BazelLabel(PACKAGE));
        bazelProjectManager.addProject(bazelProject);
        JvmClasspath bazelClasspath = new JvmUnionClasspath(bazelWorkspace, bazelProjectManager, bazelProject,
                implicitDependencyHelper, osDetector,
                bazelCommandManager,
                strategies);
        printClasspath(bazelClasspath);
    }

    // HELPERS

    private static void printClasspath(JvmClasspath bazelClasspath) {
        JvmClasspathData cpes = bazelClasspath.getClasspathEntries(null);
        System.out.println("isComplete: " + cpes.isComplete);
        for (BazelProject p : cpes.classpathProjectReferences) {
            System.out.println("Project: " + p.name);
        }
        for (JvmClasspathEntry e : cpes.jvmClasspathEntries)
            printClasspathEntry("entry", null);
    }

    private static void printClasspathEntry(String prefix, JvmClasspathEntry entry) {
        System.out.println(prefix + " ::" + entry.pathToJar);
        // TODO pathToSourceJar, isRuntimeJar, isTestJar
    }

    private static void parseArgs(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: java -jar BazelAnalyzerApp_deploy.jar [Bazel executable path] [Bazel workspace absolute path]");
        }
        bazelExecutablePath = args[0];
        bazelExecutableFile = new File(bazelExecutablePath);
        bazelExecutableFile = FSPathHelper.getCanonicalFileSafely(bazelExecutableFile);

        bazelWorkspacePath = args[1];
        bazelWorkspaceDir = new File(bazelWorkspacePath);
        bazelWorkspaceDir = FSPathHelper.getCanonicalFileSafely(bazelWorkspaceDir);

        if (!bazelExecutableFile.exists()) {
            throw new IllegalArgumentException(
                    "Bazel executable path does not exist. Usage: java -jar BazelAnalyzerApp_deploy.jar [Bazel executable path] [Bazel workspace absolute path]");
        }
        if (!bazelWorkspaceDir.exists()) {
            throw new IllegalArgumentException(
                    "Bazel workspace directory does not exist. Usage: java -jar BazelAnalyzerApp_deploy.jar [Bazel executable path] [Bazel workspace absolute path]");
        }
        if (!bazelWorkspaceDir.isDirectory()) {
            throw new IllegalArgumentException(
                    "Bazel workspace directory does not exist. Usage: java -jar BazelAnalyzerApp_deploy.jar [Bazel executable path] [Bazel workspace absolute path]");
        }

        if (args.length > 2) {
            // optional third parameter is the package to scope the analysis to (- is a
            // placeholder arg to signal there is no scope)
            if (!"-".equals(args[2])) {
                rootPackageToAnalyze = args[2];
                if (!rootPackageToAnalyze.startsWith(BazelLabel.BAZEL_ROOT_SLASHES)) {
                    throw new IllegalArgumentException(
                            "The third argument is expected to be a full Bazel package label, such as //foo/bar");
                }
                BazelLabel rootPackageToAnalyzeLabel = new BazelLabel(rootPackageToAnalyze);
                if (!rootPackageToAnalyzeLabel.isConcrete()) {
                    throw new IllegalArgumentException(
                            "The third argument is expected to be a concrete Bazel package label, such as //foo/bar");
                }
                // remove leading slashes
                rootPackageToAnalyze = rootPackageToAnalyze.substring(2);
            }
        }

        if (args.length > 3) {
            // optional fourth parameter is a comma delimited list of paths to ignore
            // - one use for this is if a package requires a huge docker base image to be
            // downloaded
            // - the path is a file path with OS specific delimiter, and points to a
            // directory
            // - example: a/b/c,a/b/d/e,a/g/h/i
            pathsToIgnore = new HashSet<>();
            String[] ignores = args[3].split(",");
            for (String ignore : ignores) {
                pathsToIgnore.add(ignore);
            }
        }
    }

    private static File loadAspectDirectory(String aspectPath) {
        File aspectDir = new File(aspectPath);
        aspectDir = FSPathHelper.getCanonicalFileSafely(aspectDir);

        if (!aspectDir.exists()) {
            throw new IllegalArgumentException(
                    "Aspect directory not found. Update the code ASPECT_LOCATION to point to the 'aspect' directory from bazel-java-sdk.");
        }
        if (!aspectDir.isDirectory()) {
            throw new IllegalArgumentException(
                    "bzljavasdk_aspect.bzl not found. Update the code ASPECT_LOCATION to point to the 'aspect' directory from bazel-java-sdk.");
        }
        File aspectFile = new File(aspectDir, "bzljavasdk_aspect.bzl");
        if (!aspectFile.exists()) {
            throw new IllegalArgumentException(
                    "bzljavasdk_aspect.bzl not found. Update the code ASPECT_LOCATION to point to the 'aspect' directory from bazel-java-sdk.");
        }
        return aspectDir;
    }

    private static void printBazelOptions(BazelWorkspaceCommandOptions bazelOptions) {
        System.out.println("\nBazel configuration options for the workspace:");
        System.out.println(bazelOptions.toString());
    }

    @SuppressWarnings("unused")
    private static void printPackageListToStdOut(BazelPackageInfo rootPackage) {
        System.out.println("\nFound packages eligible for import:");
        printPackage(rootPackage, "  ", "\n");
    }

    private static void printPackage(BazelPackageInfo pkg, String prefix, String suffix) {
        if (pkg.isWorkspaceRoot()) {
            System.out.println("WORKSPACE" + suffix);
        } else {
            System.out.println(prefix + pkg.getBazelPackageNameLastSegment() + suffix);
        }
        for (BazelPackageInfo child : pkg.getChildPackageInfos()) {
            printPackage(child, prefix + "  ", suffix);
        }
    }

    private static void printPackage(BazelPackageLocation pkg) {
        System.out.println("  " + pkg.getBazelPackageName());
    }

    private static void printRootLabels(Set<String> rootLabels) {
        System.out.println("\n\nRoot labels in the dependency tree (nothing depends on them):");
        for (String label : rootLabels) {
            System.out.println("  " + label + "\n");
        }
    }

    private static void printPackageListOrder(Iterable<BazelPackageLocation> postOrderedModules) {
        System.out.println("\n\nPackages in import order:");
        for (BazelPackageLocation loc : postOrderedModules) {
            System.out.println("  " + loc.getBazelPackageName() + "\n");
        }
    }
}
