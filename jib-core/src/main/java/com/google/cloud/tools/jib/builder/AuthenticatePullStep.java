/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryAuthenticators;
import com.google.cloud.tools.jib.registry.RegistryException;
import java.io.IOException;

/** Retrieves credentials to push from the base image registry. */
class AuthenticatePullStep extends AsyncStep<Authorization> {

  private static final String DESCRIPTION = "Authenticating with base image registry";

  private final BuildConfiguration buildConfiguration;

  AuthenticatePullStep(BuildConfiguration buildConfiguration) {
    this.buildConfiguration = buildConfiguration;
  }

  /** Depends on nothing. */
  @Override
  public Authorization call()
      throws RegistryAuthenticationFailedException, IOException, RegistryException {
    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      return RegistryAuthenticators.forOther(
              buildConfiguration.getBaseImageServerUrl(), buildConfiguration.getBaseImageName())
          .authenticate();
    }
  }
}
