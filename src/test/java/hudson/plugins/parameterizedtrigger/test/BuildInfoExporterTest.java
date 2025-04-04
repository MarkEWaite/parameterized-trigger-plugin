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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Cause.UserIdCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import hudson.plugins.parameterizedtrigger.BlockableBuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.BlockingBehaviour;
import hudson.plugins.parameterizedtrigger.BuildInfoExporterAction;
import hudson.plugins.parameterizedtrigger.CounterBuildParameterFactory;
import hudson.plugins.parameterizedtrigger.CurrentBuildParameters;
import hudson.plugins.parameterizedtrigger.PredefinedBuildParameters;
import hudson.plugins.parameterizedtrigger.TriggerBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class BuildInfoExporterTest {

    @Test
    void test(JenkinsRule r) throws Exception {
        Project<?, ?> projectA = r.createFreeStyleProject("projectA");
        List<AbstractBuildParameters> buildParameters = new ArrayList<>();
        buildParameters.add(new CurrentBuildParameters());
        BlockingBehaviour neverFail = new BlockingBehaviour("never", "never", "never");
        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", neverFail, buildParameters);
        projectA.getBuildersList().add(new TriggerBuilder(config));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        projectA.getBuildersList().add(builder);

        Project projectB = r.createFreeStyleProject("projectB");
        projectB.setQuietPeriod(0);
        r.jenkins.rebuildDependencyGraph();

        // Just to make sure they differ from projectA's build numbers.
        projectB.updateNextBuildNumber(3);

        int expectedBuildNumber = projectB.getNextBuildNumber();
        projectA.scheduleBuild2(0, new UserIdCause()).get();

        Run buildB1 = projectB.getBuildByNumber(expectedBuildNumber);
        EnvVars envVars = builder.getEnvVars();
        // System.out.println("envVars: " + envVars);

        assertThat(envVars, notNullValue());
        assertThat(envVars, hasEntry("LAST_TRIGGERED_JOB_NAME", "projectB"));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_NUMBER_projectB", Integer.toString(expectedBuildNumber)));
        assertThat(
                envVars,
                hasEntry("TRIGGERED_BUILD_RESULT_projectB", buildB1.getResult().toString()));
        assertThat(
                envVars,
                hasEntry(
                        "TRIGGERED_BUILD_RESULT_projectB_RUN_" + expectedBuildNumber,
                        buildB1.getResult().toString()));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_RUN_COUNT_projectB", "1"));
        assertThat(envVars, hasEntry("TRIGGERED_JOB_NAMES", "projectB"));

        // The below test for expectedBuildNumber is meaningless if the
        // value doesn't update, though it should always update.
        assertThat(projectB.getNextBuildNumber(), is(not(expectedBuildNumber)));

        expectedBuildNumber = projectB.getNextBuildNumber();
        AbstractBuild<?, ?> buildA2 =
                projectA.scheduleBuild2(0, new UserIdCause()).get();
        envVars = builder.getEnvVars();

        assertThat(envVars, notNullValue());
        assertThat(envVars, hasEntry("LAST_TRIGGERED_JOB_NAME", "projectB"));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_NUMBER_projectB", Integer.toString(expectedBuildNumber)));
        assertThat(
                envVars,
                hasEntry("TRIGGERED_BUILD_RESULT_projectB", buildA2.getResult().toString()));
        assertThat(
                envVars,
                hasEntry(
                        "TRIGGERED_BUILD_RESULT_projectB_RUN_" + expectedBuildNumber,
                        buildA2.getResult().toString()));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_RUN_COUNT_projectB", "1"));
        assertThat(envVars, hasEntry("TRIGGERED_JOB_NAMES", "projectB"));
    }

    @Test
    void test_oddchars(JenkinsRule r) throws Exception {
        Project<?, ?> projectA = r.createFreeStyleProject("projectA");
        List<AbstractBuildParameters> buildParameters = new ArrayList<>();
        buildParameters.add(new CurrentBuildParameters());
        BlockingBehaviour neverFail = new BlockingBehaviour("never", "never", "never");

        String testName = "odd£()+}{-=~chars-10";
        String testNameResult = "odd_chars_10";
        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig(testName, neverFail, buildParameters);
        projectA.getBuildersList().add(new TriggerBuilder(config));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        projectA.getBuildersList().add(builder);

        Project projectB = r.createFreeStyleProject(testName);
        projectB.setQuietPeriod(0);
        r.jenkins.rebuildDependencyGraph();
        // Just to make sure they differ from projectA's build numbers.
        projectB.updateNextBuildNumber(3);

        int expectedBuildNumber = projectB.getNextBuildNumber();
        projectA.scheduleBuild2(0, new UserIdCause()).get();

        EnvVars envVars = builder.getEnvVars();
        // System.out.println("envVars: " + envVars);

        assertThat(envVars, notNullValue());
        assertThat(envVars, hasEntry("LAST_TRIGGERED_JOB_NAME", testNameResult));
        assertThat(
                envVars, hasEntry("TRIGGERED_BUILD_NUMBER_" + testNameResult, Integer.toString(expectedBuildNumber)));

        // The below test for expectedBuildNumber is meaningless if the
        // value doesn't update, though it should always update.
        assertThat(projectB.getNextBuildNumber(), is(not(expectedBuildNumber)));

        expectedBuildNumber = projectB.getNextBuildNumber();
        projectA.scheduleBuild2(0, new UserIdCause()).get();
        envVars = builder.getEnvVars();

        assertThat(envVars, notNullValue());
        assertThat(envVars, hasEntry("LAST_TRIGGERED_JOB_NAME", testNameResult));
        assertThat(
                envVars, hasEntry("TRIGGERED_BUILD_NUMBER_" + testNameResult, Integer.toString(expectedBuildNumber)));
    }

    @Test
    void test_multipletriggers(JenkinsRule r) throws Exception {
        String testNameResult = "projectB";
        String testNameResult2 = "projectC";
        int buildsToTest = 5;

        Project<?, ?> projectA = r.createFreeStyleProject();
        Project projectB = r.createFreeStyleProject(testNameResult);
        Project projectC = r.createFreeStyleProject(testNameResult2);
        projectA.getBuildersList()
                .add(new TriggerBuilder(new BlockableBuildTriggerConfig(
                        testNameResult + "," + testNameResult2,
                        new BlockingBehaviour(Result.FAILURE, Result.UNSTABLE, Result.FAILURE),
                        Collections.singletonList(new CounterBuildParameterFactory(
                                "0", Integer.toString(buildsToTest - 1), "1", "TEST=COUNT$COUNT")),
                        Collections.emptyList())));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        projectA.getBuildersList().add(builder);

        projectB.setQuietPeriod(0);
        projectB.updateNextBuildNumber(3);

        projectC.setQuietPeriod(0);
        projectC.updateNextBuildNumber(12);
        r.jenkins.rebuildDependencyGraph();

        int firstExpectedBuildNumberB = projectB.getNextBuildNumber();
        int firstExpectedBuildNumberC = projectC.getNextBuildNumber();

        projectA.scheduleBuild2(0, new UserIdCause()).get();
        r.waitUntilNoActivity();

        EnvVars envVars = builder.getEnvVars();
        // System.out.println("envVars: " + envVars);

        assertEquals(buildsToTest, projectB.getBuilds().size());
        assertEquals(buildsToTest, projectC.getBuilds().size());

        StringBuilder allBuildNumbersB = new StringBuilder();
        for (int run = 1, buildNumber = firstExpectedBuildNumberB; run <= buildsToTest; run++, buildNumber++) {
            if (!allBuildNumbersB.isEmpty()) {
                allBuildNumbersB.append(",");
            }
            allBuildNumbersB.append(buildNumber);
            assertThat(envVars, hasEntry("TRIGGERED_BUILD_RESULT_projectB_RUN_" + buildNumber, "SUCCESS"));
        }

        StringBuilder allBuildNumbersC = new StringBuilder();
        for (int run = 1, buildNumber = firstExpectedBuildNumberC; run <= buildsToTest; run++, buildNumber++) {
            if (!allBuildNumbersC.isEmpty()) {
                allBuildNumbersC.append(",");
            }
            allBuildNumbersC.append(buildNumber);
            assertThat(envVars, hasEntry("TRIGGERED_BUILD_RESULT_projectC_RUN_" + buildNumber, "SUCCESS"));
        }

        int lastBuildNumberB = firstExpectedBuildNumberB + (buildsToTest - 1);
        int lastBuildNumberC = firstExpectedBuildNumberC + (buildsToTest - 1);

        assertThat(envVars, notNullValue());
        assertThat(envVars, hasEntry("LAST_TRIGGERED_JOB_NAME", testNameResult2));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_NUMBER_" + testNameResult, Integer.toString(lastBuildNumberB)));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_NUMBER_" + testNameResult2, Integer.toString(lastBuildNumberC)));
        assertThat(envVars, hasEntry("TRIGGERED_JOB_NAMES", testNameResult + "," + testNameResult2));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_NUMBERS_" + testNameResult, allBuildNumbersB.toString()));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_NUMBERS_" + testNameResult2, allBuildNumbersC.toString()));
    }

    @Test
    void testNonBlocking(JenkinsRule r) throws Exception {
        Project<?, ?> projectA = r.createFreeStyleProject("projectA");
        List<AbstractBuildParameters> buildParameters = new ArrayList<>();
        buildParameters.add(new CurrentBuildParameters());
        BlockingBehaviour neverFail = new BlockingBehaviour("never", "never", "never");
        BlockableBuildTriggerConfig config = new BlockableBuildTriggerConfig("projectB", neverFail, buildParameters);

        BlockableBuildTriggerConfig nonBlockingConfig =
                new BlockableBuildTriggerConfig("projectC", null, buildParameters);
        projectA.getBuildersList().add(new TriggerBuilder(config, nonBlockingConfig));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        projectA.getBuildersList().add(builder);

        Project projectB = r.createFreeStyleProject("projectB");
        projectB.setQuietPeriod(0);
        Project projectC = r.createFreeStyleProject("projectC");
        projectB.setQuietPeriod(0);
        r.jenkins.rebuildDependencyGraph();

        // Just to make sure they differ from projectA's build numbers.
        projectB.updateNextBuildNumber(3);
        projectC.updateNextBuildNumber(20);

        int expectedBuildNumber = projectB.getNextBuildNumber();
        int expectedBuildNumberC = projectC.getNextBuildNumber();
        projectA.scheduleBuild2(0, new UserIdCause()).get();
        r.waitUntilNoActivity();

        Run buildB1 = projectB.getBuildByNumber(expectedBuildNumber);
        Run buildC1 = projectC.getBuildByNumber(expectedBuildNumberC);
        EnvVars envVars = builder.getEnvVars();
        // System.out.println("envVars: " + envVars);

        assertThat(envVars, notNullValue());
        assertThat(envVars, hasEntry("LAST_TRIGGERED_JOB_NAME", "projectB"));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_NUMBER_projectB", Integer.toString(expectedBuildNumber)));
        assertThat(
                envVars,
                hasEntry("TRIGGERED_BUILD_RESULT_projectB", buildB1.getResult().toString()));
        assertThat(
                envVars,
                hasEntry(
                        "TRIGGERED_BUILD_RESULT_projectB_RUN_" + expectedBuildNumber,
                        buildB1.getResult().toString()));
        assertThat(envVars, hasEntry("TRIGGERED_BUILD_RUN_COUNT_projectB", "1"));
        assertThat(envVars, hasEntry("TRIGGERED_JOB_NAMES", "projectB"));
        // check that we don't see entries for projectC
        assertThat(envVars, not(hasEntry("TRIGGERED_BUILD_NUMBER_projectC", Integer.toString(expectedBuildNumberC))));
        assertThat(
                envVars,
                not(hasEntry(
                        "TRIGGERED_BUILD_RESULT_projectC_RUN_" + expectedBuildNumberC,
                        buildC1.getResult().toString())));
    }

    @Test
    void testProjectDeleted(JenkinsRule r) throws Exception {
        FreeStyleProject p1 = r.createFreeStyleProject();
        FreeStyleProject p2 = r.createFreeStyleProject();

        // Blocked build
        p1.getBuildersList()
                .add(new TriggerBuilder(new BlockableBuildTriggerConfig(
                        p2.getName(),
                        new BlockingBehaviour(Result.FAILURE, Result.UNSTABLE, Result.FAILURE),
                        List.of(new PredefinedBuildParameters("test=test")))));

        FreeStyleBuild blockedBuild = p1.scheduleBuild2(0).get();
        r.assertBuildStatusSuccess(blockedBuild);

        // Unblocked build
        p1.getBuildersList().clear();
        p1.getBuildersList()
                .add(new TriggerBuilder(new BlockableBuildTriggerConfig(
                        p2.getName(), null, List.of(new PredefinedBuildParameters("test=test")))));

        FreeStyleBuild unblockedBuild = p1.scheduleBuild2(0).get();
        r.assertBuildStatusSuccess(unblockedBuild);

        r.waitUntilNoActivity();

        assertEquals(
                1,
                blockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredBuilds()
                        .size());
        assertEquals(
                p2.getBuildByNumber(1),
                blockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredBuilds()
                        .get(0));
        assertEquals(
                0,
                blockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredProjects()
                        .size());

        assertEquals(
                0,
                unblockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredBuilds()
                        .size());
        assertEquals(
                1,
                unblockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredProjects()
                        .size());
        assertEquals(
                p2,
                unblockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredProjects()
                        .get(0));

        p2.delete();

        assertEquals(
                1,
                blockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredBuilds()
                        .size());
        assertNull(blockedBuild
                .getAction(BuildInfoExporterAction.class)
                .getTriggeredBuilds()
                .get(0));
        assertEquals(
                0,
                blockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredProjects()
                        .size());

        assertEquals(
                0,
                unblockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredBuilds()
                        .size());
        assertEquals(
                1,
                unblockedBuild
                        .getAction(BuildInfoExporterAction.class)
                        .getTriggeredProjects()
                        .size());
        assertNull(unblockedBuild
                .getAction(BuildInfoExporterAction.class)
                .getTriggeredProjects()
                .get(0));
    }

    @LocalData
    @Test
    void testMigrateFrom221(JenkinsRule r) throws Exception {
        // lastReference should be preserved after migration.
        String lastReferenceValue = null;

        {
            FreeStyleProject p = r.jenkins.getItemByFullName("upstream", FreeStyleProject.class);
            assertNotNull(p);
            FreeStyleBuild b = p.getLastBuild();
            assertNotNull(b);
            BuildInfoExporterAction action = b.getAction(BuildInfoExporterAction.class);
            assertNotNull(action);

            // action should contain following builds:
            //  downstream1#1
            //  downstream1#2
            //  downstream2#1

            Set<Run<?, ?>> expected = new HashSet<>(Arrays.asList(
                    r.jenkins
                            .getItemByFullName("downstream1", FreeStyleProject.class)
                            .getBuildByNumber(1),
                    r.jenkins
                            .getItemByFullName("downstream1", FreeStyleProject.class)
                            .getBuildByNumber(2),
                    r.jenkins
                            .getItemByFullName("downstream2", FreeStyleProject.class)
                            .getBuildByNumber(1)));

            assertEquals(expected, new HashSet<>(action.getTriggeredBuilds()));

            EnvVars env = new EnvVars();
            action.buildEnvVars(b, env);
            lastReferenceValue = env.get(BuildInfoExporterAction.JOB_NAME_VARIABLE);
            assertEquals("downstream1", lastReferenceValue);

            b.save();
        }

        {
            FreeStyleProject p = r.jenkins.getItemByFullName("upstream", FreeStyleProject.class);
            assertNotNull(p);
            FreeStyleBuild b = p.getLastBuild();
            assertNotNull(b);
            BuildInfoExporterAction action = b.getAction(BuildInfoExporterAction.class);
            assertNotNull(action);

            // action should contain following builds:
            //  downstream1#1
            //  downstream1#2
            //  downstream2#1

            Set<Run<?, ?>> expected = new HashSet<>(Arrays.asList(
                    r.jenkins
                            .getItemByFullName("downstream1", FreeStyleProject.class)
                            .getBuildByNumber(1),
                    r.jenkins
                            .getItemByFullName("downstream1", FreeStyleProject.class)
                            .getBuildByNumber(2),
                    r.jenkins
                            .getItemByFullName("downstream2", FreeStyleProject.class)
                            .getBuildByNumber(1)));

            assertEquals(expected, new HashSet<>(action.getTriggeredBuilds()));

            EnvVars env = new EnvVars();
            action.buildEnvVars(b, env);
            assertEquals(lastReferenceValue, env.get(BuildInfoExporterAction.JOB_NAME_VARIABLE));
        }
    }
}
