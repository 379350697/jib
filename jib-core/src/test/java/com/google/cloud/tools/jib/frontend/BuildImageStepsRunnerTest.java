/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BuildImageStepsRunner}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildImageStepsRunnerTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildImageSteps mockBuildImageSteps;
  @Mock private SourceFilesConfiguration mockSourceFilesConfiguration;
  @Mock private RegistryUnauthorizedException mockRegistryUnauthorizedException;
  @Mock private HttpResponseException mockHttpResponseException;
  @Mock private ExecutionException mockExecutionException;
  @Mock private BuildConfiguration mockBuildConfiguration;

  private BuildImageStepsRunner testBuildImageStepsRunner;

  @Before
  public void setUpMocks() {
    testBuildImageStepsRunner = new BuildImageStepsRunner(() -> mockBuildImageSteps);

    Mockito.when(mockBuildImageSteps.getBuildConfiguration()).thenReturn(mockBuildConfiguration);
  }

  @Test
  public void testBuildImage_pass() throws BuildImageStepsExecutionException {
    testBuildImageStepsRunner.buildImage();
  }

  @Test
  public void testBuildImage_cacheMetadataCorruptedException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    CacheMetadataCorruptedException mockCacheMetadataCorruptedException =
        Mockito.mock(CacheMetadataCorruptedException.class);
    Mockito.doThrow(mockCacheMetadataCorruptedException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          "Build image failed, perhaps you should run 'mvn clean' to clear the cache",
          ex.getMessage());
      Assert.assertEquals(mockCacheMetadataCorruptedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_httpHostConnectException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    HttpHostConnectException mockHttpHostConnectException =
        Mockito.mock(HttpHostConnectException.class);
    Mockito.when(mockExecutionException.getCause()).thenReturn(mockHttpHostConnectException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          "Build image failed, perhaps you should make sure your Internet is up and that the registry you are pushing to exists",
          ex.getMessage());
      Assert.assertEquals(mockHttpHostConnectException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_unknownHostException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    UnknownHostException mockUnknownHostException = Mockito.mock(UnknownHostException.class);
    Mockito.when(mockExecutionException.getCause()).thenReturn(mockUnknownHostException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          "Build image failed, perhaps you should make sure that the registry you configured exists/is spelled properly",
          ex.getMessage());
      Assert.assertEquals(mockUnknownHostException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_statusCodeForbidden()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getImageReference())
        .thenReturn("someregistry/somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_FORBIDDEN);

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          "Build image failed, perhaps you should make sure you have permissions for someregistry/somerepository",
          ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_noCredentials()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    Mockito.when(mockBuildConfiguration.getBaseImageRegistry()).thenReturn("someregistry");

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          "Build image failed, perhaps you should set a credential helper name with the configuration 'credHelpers' or set credentials for 'someregistry' in your Maven settings",
          ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_other()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    Mockito.when(mockBuildConfiguration.getBaseImageRegistry()).thenReturn("someregistry");
    Mockito.when(mockBuildConfiguration.getBaseImageCredentialHelperName())
        .thenReturn("some-credential-helper");

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          "Build image failed, perhaps you should make sure your credentials for 'someregistry' are set up correctly",
          ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_other()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Throwable throwable = new Throwable();
    Mockito.when(mockExecutionException.getCause()).thenReturn(throwable);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals("Build image failed", ex.getMessage());
      Assert.assertEquals(throwable, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_otherException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    IOException ioException = new IOException();
    Mockito.doThrow(ioException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals("Build image failed", ex.getMessage());
      Assert.assertEquals(ioException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_cacheDirectoryNotOwnedException()
      throws InterruptedException, ExecutionException, CacheDirectoryNotOwnedException,
          CacheMetadataCorruptedException, IOException {
    Path expectedCacheDirectory = Paths.get("some/path");

    CacheDirectoryNotOwnedException mockCacheDirectoryNotOwnedException =
        Mockito.mock(CacheDirectoryNotOwnedException.class);
    Mockito.when(mockCacheDirectoryNotOwnedException.getCacheDirectory())
        .thenReturn(expectedCacheDirectory);
    Mockito.doThrow(mockCacheDirectoryNotOwnedException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage();
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          "Build image failed, perhaps you should check that '"
              + expectedCacheDirectory
              + "' is not used by another application or set the `useOnlyProjectCache` configuration",
          ex.getMessage());
      Assert.assertEquals(mockCacheDirectoryNotOwnedException, ex.getCause());
    }
  }
}