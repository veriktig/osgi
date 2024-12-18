/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/

package org.osgi.service.featurelauncher;

import org.osgi.service.feature.FeatureConfiguration;
import org.osgi.service.feature.FeatureExtension;
import org.osgi.service.feature.FeatureExtension.Kind;
import org.osgi.service.feature.FeatureExtension.Type;
import org.osgi.service.feature.ID;

/**
 * Defines standard constants for the Feature Launcher specification.
 * 
 * @author $Id$
 */
public final class FeatureLauncherConstants {
	private FeatureLauncherConstants() {
		// non-instantiable
	}

	/**
	 * The name of the implementation capability for the Feature specification.
	 */
	public static final String	FEATURE_LAUNCHER_IMPLEMENTATION					= "osgi.featurelauncher";

	/**
	 * The version of the implementation capability for the Feature
	 * specification.
	 */
	public static final String	FEATURE_LAUNCHER_SPECIFICATION_VERSION			= "1.0";

	/**
	 * The configuration property used to set the timeout for creating
	 * configurations from {@link FeatureConfiguration} definitions.
	 * <p>
	 * The value must be a {@link Long} indicating the number of milliseconds
	 * that the implementation should wait to be able to create configurations
	 * for the Feature. The default is <code>5000</code>.
	 * <p>
	 * A value of <code>0</code> means that the configurations must be created
	 * before the bundles in the feature are started. In general this will
	 * require the <code>ConfigurationAdmin</code> service to be available from
	 * outside the feature.
	 * <p>
	 * A value of <code>-1</code> means that the implementation must not wait to
	 * create configurations and should return control to the user as soon as
	 * the bundles are started, even if the configurations have not yet been
	 * created.
	 */
	public static final String	CONFIGURATION_TIMEOUT							= "configuration.timeout";

	/**
	 * The name for the {@link FeatureExtension} which defines the framework
	 * that should be used to launch the feature. The extension must be of
	 * {@link Type#ARTIFACTS} and contain one or more {@link ID} entries
	 * corresponding to OSGi framework implementations. This extension must be
	 * processed even if it is {@link Kind#OPTIONAL} or {@link Kind#TRANSIENT}.
	 * <p>
	 * If more than one framework entry is provided then the list will be used
	 * as a priority order when determining the framework implementation to use.
	 * If none of the frameworks are present then an error is raised and
	 * launching will be aborted.
	 */
	public static final String	LAUNCH_FRAMEWORK								= "launch-framework";

	/**
	 * The name for the {@link FeatureExtension} of {@link Type#JSON} which
	 * defines the framework properties that should be used when launching the
	 * feature.
	 */
	public static final String	FRAMEWORK_LAUNCHING_PROPERTIES					= "framework-launching-properties";

	/**
	 * The property name for the <code>JSON<code> property which defines the
	 * version of the framework properties extension used in this feature.
	 */
	public static final String	FRAMEWORK_LAUNCHING_PROPERTIES_VERSION			= "_osgi_featurelauncher_launchprops_version";

	/**
	 * The name of the metadata property used to indicate the start level of the
	 * bundle to be installed. The value must be an integer between
	 * <code>0</code> and {@link Integer#MAX_VALUE}.
	 */
	public static final String	BUNDLE_START_LEVEL_METADATA						= "bundleStartLevel";

	/**
	 * The name for the {@link FeatureExtension} of {@link Type#JSON} which
	 * defines the start level configuration for the bundles in the feature
	 */
	public static final String	BUNDLE_START_LEVELS								= "bundle-start-levels";
}
