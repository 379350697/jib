/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JavaDockerContextGenerator}. */
@RunWith(MockitoJUnitRunner.class)
public class JavaDockerContextGeneratorTest {

  private static final Path EXPECTED_DEPENDENCIES_PATH = Paths.get("/app/libs/");
  private static final Path EXPECTED_RESOURCES_PATH = Paths.get("/app/resources/");
  private static final Path EXPECTED_CLASSES_PATH = Paths.get("/app/classes/");

  private static void assertSameFiles(Path directory1, Path directory2) throws IOException {
    Deque<Path> directory1Paths = new ArrayDeque<>(new DirectoryWalker(directory1).walk());

    new DirectoryWalker(directory2)
        .walk(
            directory2Path ->
                Assert.assertEquals(
                    directory1.relativize(directory1Paths.pop()),
                    directory2.relativize(directory2Path)));

    Assert.assertEquals(0, directory1Paths.size());
  }

  private static ImmutableList<Path> listFilesInDirectory(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files.collect(ImmutableList.toImmutableList());
    }
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JavaLayerConfigurations mockJavaLayerConfigurations;

  @Test
  public void testGenerate() throws IOException, URISyntaxException {
    Path testDependencies = Paths.get(Resources.getResource("application/dependencies").toURI());
    Path testSnapshotDependencies =
        Paths.get(Resources.getResource("application/snapshot-dependencies").toURI());
    Path testResources = Paths.get(Resources.getResource("application/resources").toURI());
    Path testClasses = Paths.get(Resources.getResource("application/classes").toURI());
    Path testExtraFiles = Paths.get(Resources.getResource("layer").toURI());

    ImmutableList<Path> expectedDependenciesFiles = listFilesInDirectory(testDependencies);
    ImmutableList<Path> expectedSnapshotDependenciesFiles =
        listFilesInDirectory(testSnapshotDependencies);
    ImmutableList<Path> expectedResourcesFiles = listFilesInDirectory(testResources);
    ImmutableList<Path> expectedClassesFiles = listFilesInDirectory(testClasses);
    ImmutableList<Path> expectedExtraFiles = listFilesInDirectory(testExtraFiles);

    Path targetDirectory = temporaryFolder.newFolder().toPath();

    /*
     * Deletes the directory so that JavaDockerContextGenerator#generate does not throw
     * InsecureRecursiveDeleteException.
     */
    Files.delete(targetDirectory);

    Mockito.when(mockJavaLayerConfigurations.getDependencyLayerEntries())
        .thenReturn(
            expectedDependenciesFiles
                .stream()
                .map(
                    sourceFile ->
                        new LayerEntry(
                            sourceFile,
                            EXPECTED_DEPENDENCIES_PATH.resolve(sourceFile.getFileName())))
                .collect(ImmutableList.toImmutableList()));
    Mockito.when(mockJavaLayerConfigurations.getSnapshotDependencyLayerEntries())
        .thenReturn(
            expectedSnapshotDependenciesFiles
                .stream()
                .map(
                    sourceFile ->
                        new LayerEntry(
                            sourceFile,
                            EXPECTED_DEPENDENCIES_PATH.resolve(sourceFile.getFileName())))
                .collect(ImmutableList.toImmutableList()));
    Mockito.when(mockJavaLayerConfigurations.getResourceLayerEntries())
        .thenReturn(
            expectedResourcesFiles
                .stream()
                .map(
                    sourceFile ->
                        new LayerEntry(
                            sourceFile, EXPECTED_RESOURCES_PATH.resolve(sourceFile.getFileName())))
                .collect(ImmutableList.toImmutableList()));
    Mockito.when(mockJavaLayerConfigurations.getClassLayerEntries())
        .thenReturn(
            expectedClassesFiles
                .stream()
                .map(
                    sourceFile ->
                        new LayerEntry(
                            sourceFile, EXPECTED_CLASSES_PATH.resolve(sourceFile.getFileName())))
                .collect(ImmutableList.toImmutableList()));
    Mockito.when(mockJavaLayerConfigurations.getExtraFilesLayerEntries())
        .thenReturn(
            expectedExtraFiles
                .stream()
                .map(
                    sourceFile ->
                        new LayerEntry(
                            sourceFile, Paths.get("/").resolve(sourceFile.getFileName())))
                .collect(ImmutableList.toImmutableList()));
    new JavaDockerContextGenerator(mockJavaLayerConfigurations)
        .setBaseImage("somebaseimage")
        .generate(targetDirectory);

    Assert.assertTrue(Files.exists(targetDirectory.resolve("Dockerfile")));
    assertSameFiles(targetDirectory.resolve("libs"), testDependencies);
    assertSameFiles(targetDirectory.resolve("snapshot-libs"), testSnapshotDependencies);
    assertSameFiles(targetDirectory.resolve("resources"), testResources);
    assertSameFiles(targetDirectory.resolve("classes"), testClasses);
    assertSameFiles(targetDirectory.resolve("root"), testExtraFiles);
  }

  @Test
  public void testMakeDockerfile() throws IOException {
    String expectedBaseImage = "somebaseimage";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "another\"Flag");
    String expectedMainClass = "SomeMainClass";
    List<String> expectedJavaArguments = Arrays.asList("arg1", "arg2");
    Map<String, String> expectedEnv = ImmutableMap.of("key1", "value1", "key2", "value2");
    List<String> exposedPorts = Arrays.asList("1000/tcp", "2000-2010/udp");
    Map<String, String> expectedLabels =
        ImmutableMap.of(
            "key1",
            "value",
            "key2",
            "value with\\backslashes\"and\\\\\"\"quotes\"\\",
            "key3",
            "value3");

    Mockito.when(mockJavaLayerConfigurations.getDependencyLayerEntries())
        .thenReturn(
            ImmutableList.of(new LayerEntry(Paths.get("ignored"), EXPECTED_DEPENDENCIES_PATH)));
    Mockito.when(mockJavaLayerConfigurations.getSnapshotDependencyLayerEntries())
        .thenReturn(
            ImmutableList.of(new LayerEntry(Paths.get("ignored"), EXPECTED_DEPENDENCIES_PATH)));
    Mockito.when(mockJavaLayerConfigurations.getResourceLayerEntries())
        .thenReturn(
            ImmutableList.of(new LayerEntry(Paths.get("ignored"), EXPECTED_RESOURCES_PATH)));
    Mockito.when(mockJavaLayerConfigurations.getClassLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(Paths.get("ignored"), EXPECTED_CLASSES_PATH)));
    Mockito.when(mockJavaLayerConfigurations.getExtraFilesLayerEntries())
        .thenReturn(ImmutableList.of(new LayerEntry(Paths.get("ignored"), Paths.get("/"))));
    String dockerfile =
        new JavaDockerContextGenerator(mockJavaLayerConfigurations)
            .setBaseImage(expectedBaseImage)
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(
                    expectedJvmFlags, expectedMainClass))
            .setJavaArguments(expectedJavaArguments)
            .setEnvironment(expectedEnv)
            .setExposedPorts(exposedPorts)
            .setLabels(expectedLabels)
            .makeDockerfile();

    // Need to split/rejoin the string here to avoid cross-platform troubles
    List<String> sampleDockerfile =
        Resources.readLines(Resources.getResource("sampleDockerfile"), StandardCharsets.UTF_8);
    Assert.assertEquals(String.join("\n", sampleDockerfile), dockerfile);
  }
}
