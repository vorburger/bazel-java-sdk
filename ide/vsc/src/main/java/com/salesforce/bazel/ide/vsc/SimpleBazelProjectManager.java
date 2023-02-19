package com.salesforce.bazel.ide.vsc;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SimpleBazelProjectManager extends BazelProjectManager {

    @Override
    public BazelProject getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setProjectReferences(BazelProject thisProject, List<BazelProject> updatedRefList) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getBazelLabelForProject(BazelProject bazelProject) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<BazelLabel, BazelProject> getBazelLabelToProjectMap(Collection<BazelProject> bazelProjects) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BazelProjectTargets getConfiguredBazelTargets(BazelProject bazelProject, boolean addWildcardIfNoTargets) {
        BazelProjectTargets activatedTargets = new BazelProjectTargets(bazelProject,
                bazelProject.getProjectStructure().getBazelTargets().get(0).getLabelPath());
        Set<String> activeTargets = new TreeSet<>();
        if (addWildcardIfNoTargets) {
            activeTargets.add("//...");
        }
        activatedTargets.activateSpecificTargets(activeTargets);
        return activatedTargets;
    }

    @Override
    public List<String> getBazelBuildFlagsForProject(BazelProject bazelProject) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot, String bazelProjectPackage,
            List<BazelLabel> bazelTargets, List<String> bazelBuildFlags) {
        // TODO Auto-generated method stub

    }
}
