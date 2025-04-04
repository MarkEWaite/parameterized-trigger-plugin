/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.parameterizedtrigger.test;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import hudson.plugins.parameterizedtrigger.BlockableBuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.CurrentBuildParameters;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.plugins.parameterizedtrigger.TriggerBuilder;
import hudson.tasks.BuildStep;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.ModifiableTopLevelItemGroup;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.jenkinsci.plugins.conditionalbuildstep.ConditionalBuilder;
import org.jenkinsci.plugins.conditionalbuildstep.singlestep.SingleConditionalBuilder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RenameJobTest {

    @Test
    void testRenameAndDeleteJobSingleProject(JenkinsRule r) throws Exception {
        FreeStyleProject projectA = r.createFreeStyleProject("projectA");
        configureTriggeringOf(projectA, "projectB");
        Project<?, ?> projectB = r.createFreeStyleProject("projectB");
        r.jenkins.rebuildDependencyGraph();

        projectB.renameTo("projectB-renamed");

        assertTriggering(projectA, "projectB-renamed");

        projectB.delete();

        // confirm projectA's build step trigger is updated automatically:
        assertNull(
                projectA.getBuildersList().get(TriggerBuilder.class), "now-empty build step trigger should be removed");

        // confirm projectA's post build trigger is updated automatically:
        assertNull(
                projectA.getPublishersList().get(BuildTrigger.class), "now-empty post build trigger should be removed");
    }

    @Test
    void testRenameAndDeleteJobMultipleProjects(JenkinsRule r) throws Exception {
        Project<?, ?> projectA = r.createFreeStyleProject("projectA");
        configureTriggeringOf(projectA, "projectB", "projectC");
        Project<?, ?> projectB = r.createFreeStyleProject("projectB");
        r.createFreeStyleProject("projectC");
        r.jenkins.rebuildDependencyGraph();

        projectB.renameTo("projectB-renamed");

        assertTriggering(projectA, "projectB-renamed,projectC");

        projectB.delete();

        assertTriggering(projectA, "projectC");
    }

    /**
     * Configure all the triggers to point to a set of child jobs.
     *
     * @see {@link #assertTriggering(Project, String)}
     */
    private Project<?, ?> configureTriggeringOf(Project<?, ?> project, String... childJobNames) {
        List<AbstractBuildParameters> buildParameters = new ArrayList<>();
        buildParameters.add(new CurrentBuildParameters());

        StringBuilder childJobNamesString = new StringBuilder();
        for (String childJobName : childJobNames) {
            childJobNamesString.append(childJobName);
            childJobNamesString.append(",");
        }

        // setup build step trigger
        project.getBuildersList()
                .add(new TriggerBuilder(
                        new BlockableBuildTriggerConfig(childJobNamesString.toString(), null, buildParameters)));

        // setup triggers for conditional buildsteps
        // test conditional builder (multi)
        List<BuildStep> blist = new ArrayList<>();
        TriggerBuilder tb = new TriggerBuilder(
                new BlockableBuildTriggerConfig(childJobNamesString.toString(), null, buildParameters));
        blist.add(tb);
        project.getBuildersList().add(new ConditionalBuilder(new AlwaysRun(), new BuildStepRunner.Run(), blist));

        // test conditional builder (single)
        TriggerBuilder tb2 = new TriggerBuilder(
                new BlockableBuildTriggerConfig(childJobNamesString.toString(), null, buildParameters));
        project.getBuildersList().add(new SingleConditionalBuilder(tb2, new AlwaysRun(), new BuildStepRunner.Run()));

        // setup post build trigger
        project.getPublishersList()
                .add(new BuildTrigger(new BuildTriggerConfig(
                        childJobNamesString.toString(), ResultCondition.SUCCESS, new CurrentBuildParameters())));
        return project;
    }

    /**
     * Assert that all the triggers refers to expected jobs.
     */
    private void assertTriggering(Project<?, ?> p, String expected) {
        String actual = p.getBuildersList()
                .get(TriggerBuilder.class)
                .getConfigs()
                .get(0)
                .getProjects();
        assertEquals(expected, actual, "build step trigger");

        final TriggerBuilder triggerBuilder = (TriggerBuilder) p.getBuildersList()
                .getAll(ConditionalBuilder.class)
                .get(0)
                .getConditionalbuilders()
                .get(0);
        actual = triggerBuilder.getConfigs().get(0).getProjects();
        assertEquals(expected, actual, "build step trigger project within first conditionalbuildstep");

        final TriggerBuilder singleCondTrigger0 = (TriggerBuilder) p.getBuildersList()
                .getAll(SingleConditionalBuilder.class)
                .get(0)
                .getBuildStep();
        actual = singleCondTrigger0.getConfigs().get(0).getProjects();
        assertEquals(expected, actual, "build step trigger project within first singleconditionalbuildstep");

        actual = p.getPublishersList()
                .get(BuildTrigger.class)
                .getConfigs()
                .get(0)
                .getProjects();
        assertEquals(expected, actual, "post build step trigger");
    }

    private static <T extends TopLevelItem> T createProject(
            JenkinsRule r, Class<T> type, ModifiableTopLevelItemGroup owner, String name) throws IOException {
        return (T) owner.createProject((TopLevelItemDescriptor) r.jenkins.getDescriptorOrDie(type), name, true);
    }

    @Test
    void testRenameAndDeleteJobInSameFolder(JenkinsRule r) throws Exception {
        MockFolder folder1 = createProject(r, MockFolder.class, r.jenkins, "Folder1");
        FreeStyleProject p1 = createProject(r, FreeStyleProject.class, folder1, "ProjectA");
        FreeStyleProject p2 = createProject(r, FreeStyleProject.class, folder1, "ProjectB");

        p1.getPublishersList()
                .add(new BuildTrigger(new BuildTriggerConfig(
                        p2.getName(), // This should not be getFullName().
                        ResultCondition.ALWAYS,
                        true,
                        List.of(new CurrentBuildParameters()))));

        r.jenkins.rebuildDependencyGraph();

        // Test this works
        {
            assertNull(p2.getLastBuild());

            r.buildAndAssertSuccess(p1);

            r.jenkins.getQueue().getItem(p2).getFuture().get(60, TimeUnit.SECONDS);
            FreeStyleBuild b = p2.getLastBuild();
            assertNotNull(b);
            r.assertBuildStatusSuccess(b);
            b.delete();
        }

        // Rename
        p2.renameTo("ProjectB-renamed");
        assertEquals("ProjectB-renamed", p2.getName());

        // assertRenamed
        assertEquals(
                p2.getName(),
                p1.getPublishersList()
                        .get(BuildTrigger.class)
                        .getConfigs()
                        .get(0)
                        .getProjects());

        // Test this works
        {
            assertNull(p2.getLastBuild());

            r.buildAndAssertSuccess(p1);

            r.jenkins.getQueue().getItem(p2).getFuture().get(60, TimeUnit.SECONDS);
            FreeStyleBuild b = p2.getLastBuild();
            assertNotNull(b);
            r.assertBuildStatusSuccess(b);
            b.delete();
        }

        p2.delete();
        assertNull(p1.getPublishersList().get(BuildTrigger.class));
    }

    @Test
    void testRenameAndDeleteJobInSubFolder(JenkinsRule r) throws Exception {
        MockFolder folder1 = createProject(r, MockFolder.class, r.jenkins, "Folder1");
        MockFolder folder2 = createProject(r, MockFolder.class, folder1, "Folder2");
        FreeStyleProject p1 = createProject(r, FreeStyleProject.class, folder1, "ProjectA");
        FreeStyleProject p2 = createProject(r, FreeStyleProject.class, folder2, "ProjectB");

        p1.getPublishersList()
                .add(new BuildTrigger(new BuildTriggerConfig(
                        String.format("%s/%s", folder2.getName(), p2.getName()),
                        // This should not be getFullName().
                        ResultCondition.ALWAYS,
                        true,
                        null,
                        List.of(new CurrentBuildParameters()),
                        false)));

        r.jenkins.rebuildDependencyGraph();

        // Test this works
        {
            assertNull(p2.getLastBuild());

            r.buildAndAssertSuccess(p1);

            r.jenkins.getQueue().getItem(p2).getFuture().get(60, TimeUnit.SECONDS);
            FreeStyleBuild b = p2.getLastBuild();
            assertNotNull(b);
            r.assertBuildStatusSuccess(b);
            b.delete();
        }

        // Rename
        p2.renameTo("ProjectB-renamed");
        assertEquals("ProjectB-renamed", p2.getName());

        // assertRenamed
        assertEquals(
                String.format("%s/%s", folder2.getName(), p2.getName()),
                p1.getPublishersList()
                        .get(BuildTrigger.class)
                        .getConfigs()
                        .get(0)
                        .getProjects());

        // Test this works
        {
            assertNull(p2.getLastBuild());

            r.buildAndAssertSuccess(p1);

            r.jenkins.getQueue().getItem(p2).getFuture().get(60, TimeUnit.SECONDS);
            FreeStyleBuild b = p2.getLastBuild();
            assertNotNull(b);
            r.assertBuildStatusSuccess(b);
            b.delete();
        }

        p2.delete();
        assertNull(p1.getPublishersList().get(BuildTrigger.class));
    }

    @Test
    void testRenameAndDeleteJobInParentFolder(JenkinsRule r) throws Exception {
        MockFolder folder1 = createProject(r, MockFolder.class, r.jenkins, "Folder1");
        MockFolder folder2 = createProject(r, MockFolder.class, folder1, "Folder2");
        FreeStyleProject p1 = createProject(r, FreeStyleProject.class, folder2, "ProjectA");
        FreeStyleProject p2 = createProject(r, FreeStyleProject.class, folder1, "ProjectB");

        p1.getPublishersList()
                .add(new BuildTrigger(new BuildTriggerConfig(
                        String.format("../%s", p2.getName()),
                        // This should not be getFullName().
                        ResultCondition.ALWAYS,
                        true,
                        null,
                        List.of(new CurrentBuildParameters()),
                        false)));

        r.jenkins.rebuildDependencyGraph();

        // Test this works
        {
            assertNull(p2.getLastBuild());

            r.buildAndAssertSuccess(p1);

            r.jenkins.getQueue().getItem(p2).getFuture().get(60, TimeUnit.SECONDS);
            FreeStyleBuild b = p2.getLastBuild();
            assertNotNull(b);
            r.assertBuildStatusSuccess(b);
            b.delete();
        }

        // Rename
        p2.renameTo("ProjectB-renamed");
        assertEquals("ProjectB-renamed", p2.getName());

        // assertRenamed
        assertEquals(
                String.format("../%s", p2.getName()),
                p1.getPublishersList()
                        .get(BuildTrigger.class)
                        .getConfigs()
                        .get(0)
                        .getProjects());

        // Test this works
        {
            assertNull(p2.getLastBuild());

            r.buildAndAssertSuccess(p1);

            r.jenkins.getQueue().getItem(p2).getFuture().get(60, TimeUnit.SECONDS);
            FreeStyleBuild b = p2.getLastBuild();
            assertNotNull(b);
            r.assertBuildStatusSuccess(b);
            b.delete();
        }

        p2.delete();
        assertNull(p1.getPublishersList().get(BuildTrigger.class));
    }

    /**
     * {@link hudson.model.Items#computeRelativeNamesAfterRenaming(String, String, String, hudson.model.ItemGroup)} has a bug
     * that renaming names that contains the target name as prefix.
     * E.g. renaming ProjectB to ProjectB-renamed results in renaming ProjectB2 to ProjectB-renamed2.
     * This is fixed in Jenkins 1.530.
     * <p>
     * This test verifies this plugin is not affected by that problem.
     */
    @Test
    void testComputeRelativeNamesAfterRenaming(JenkinsRule r) throws Exception {
        FreeStyleProject projectA = r.createFreeStyleProject("ProjectA");
        FreeStyleProject projectB = r.createFreeStyleProject("ProjectB");
        FreeStyleProject projectB2 = r.createFreeStyleProject("ProjectB2");

        projectA.getPublishersList()
                .add(new BuildTrigger(new BuildTriggerConfig(
                        String.format("%s,%s", projectB.getName(), projectB2.getName()),
                        ResultCondition.ALWAYS,
                        true,
                        null,
                        List.of(new CurrentBuildParameters()),
                        false)));

        r.jenkins.rebuildDependencyGraph();

        projectB.renameTo("ProjectB-renamed");

        // assertRenamed
        assertEquals(
                String.format("%s,%s", projectB.getName(), projectB2.getName()),
                projectA.getPublishersList()
                        .get(BuildTrigger.class)
                        .getConfigs()
                        .get(0)
                        .getProjects());
    }
}
