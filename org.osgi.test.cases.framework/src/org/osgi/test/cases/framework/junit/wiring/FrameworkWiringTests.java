/*
 * Copyright (c) OSGi Alliance (2010). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.osgi.test.cases.framework.junit.wiring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import junit.framework.AssertionFailedError;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.test.support.OSGiTestCase;

public class FrameworkWiringTests extends OSGiTestCase {
	private final List bundles = new ArrayList();
	FrameworkWiring frameworkWiring;
	
	
	public Bundle install(String bundle) {
		Bundle result = null;
		try {
			result = super.install(bundle);
		} catch (BundleException e) {
			fail("failed to install bundle: " + bundle, e);
		} catch (IOException e) {
			fail("failed to install bundle: " + bundle, e);
		}
		if (!bundles.contains(result))
			bundles.add(result);
		return result;
	}

	protected void setUp() throws Exception {
		bundles.clear();
		frameworkWiring = (FrameworkWiring) getContext().getBundle(0).adapt(FrameworkWiring.class);
	}

	protected void tearDown() throws Exception {
		for (Iterator iBundles = bundles.iterator(); iBundles.hasNext();)
			try {
				((Bundle) iBundles.next()).uninstall();
			} catch (BundleException e) {
				// nothing
			} catch (IllegalStateException e) {
				// happens if the test uninstalls the bundle itself
			}
		refreshBundles(bundles);
		bundles.clear();
	}

	private void refreshBundles(List bundles) {
		final boolean[] done = new boolean[] {false};
		FrameworkListener listener = new FrameworkListener() {
			public void frameworkEvent(FrameworkEvent event) {
				synchronized (done) {
					if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
						done[0] = true;
						done.notify();
					}
				}
			}
		};
		frameworkWiring.refreshBundles(bundles, new FrameworkListener[] {listener});
		synchronized (done) {
			if (!done[0])
				try {
					done.wait(5000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					fail("Unexepected interruption.", e);
				}
			if (!done[0])
				fail("Timed out waiting for refresh bundles to finish.");
		}
	}

	public void testRefreshListeners() {
		Bundle tb1 = install("resolver.tb1.v110.jar");
		Bundle tb2 = install("resolver.tb2.jar");
		Bundle tb3 = install("resolver.tb3.jar");
		Bundle tb4 = install("resolver.tb4.jar");
		Bundle tb5 = install("resolver.tb5.jar");

		Collection testBundles = Arrays.asList(new Bundle[]{tb1, tb2, tb3, tb4, tb5});

		final boolean[] called = new boolean[] {false};
		final AssertionFailedError error[] = new AssertionFailedError[1];
		frameworkWiring.refreshBundles(testBundles, new FrameworkListener[] { 
				new FrameworkListener() {
					public void frameworkEvent(FrameworkEvent event) {
						synchronized (called) {
							try {
								called[0] = true;
								assertEquals("Unexpected event type", FrameworkEvent.PACKAGES_REFRESHED, event.getType());
								called.notify();
							} catch (AssertionFailedError e) {
								if (error[0] == null)
									error[0] = e;
							}
						}
					}
				}
			}
		);
		synchronized (called) {
			if (!called[0])
				try {
					called.wait(5000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					fail("Unexepected interruption.", e);
				}
			if (!called[0])
				fail("Failed to call specified listener");
			if (error[0] != null)
				throw error[0];
		}
	}

	public void testResolveBundles() {
		Bundle tb1 = install("resolver.tb1.v110.jar");
		Bundle tb2 = install("resolver.tb2.jar");
		Bundle tb3 = install("resolver.tb3.jar");
		Bundle tb4 = install("resolver.tb4.jar");
		Bundle tb5 = install("resolver.tb5.jar");

		Collection testBundles = Arrays.asList(new Bundle[]{tb1, tb2, tb3, tb4, tb5});
		assertTrue(frameworkWiring.resolveBundles(testBundles));

		assertEquals("Wrong state for bundle: " + tb1, Bundle.RESOLVED, tb1.getState());
		assertEquals("Wrong state for bundle: " + tb2, Bundle.RESOLVED, tb2.getState());
		assertEquals("Wrong state for bundle: " + tb3, Bundle.RESOLVED, tb3.getState());
		assertEquals("Wrong state for bundle: " + tb4, Bundle.RESOLVED, tb4.getState());
		assertEquals("Wrong state for bundle: " + tb5, Bundle.RESOLVED, tb5.getState());
	}

	public void testDependencyClosure() {
		Bundle tb1 = install("resolver.tb1.v110.jar");
		Bundle tb2 = install("resolver.tb2.jar");
		Bundle tb3 = install("resolver.tb3.jar");
		Bundle tb4 = install("resolver.tb4.jar");
		Bundle tb5 = install("resolver.tb5.jar");

		Collection testBundles = Arrays.asList(new Bundle[]{tb1, tb2, tb3, tb4, tb5});
		assertTrue(frameworkWiring.resolveBundles(testBundles));

		Collection closure = frameworkWiring.getDependencyClosure(Arrays.asList(new Bundle[]{tb1}));
		assertEquals("Wrong number in closure", 5, closure.size());
		assertTrue("Wrong bundles in closure: " + closure, closure.containsAll(testBundles));
		assertTrue("Wrong bundles in closure: " + closure, testBundles.containsAll(closure));

		closure = frameworkWiring.getDependencyClosure(Arrays.asList(new Bundle[]{tb2}));
		assertEquals("Wrong number in closure", 1, closure.size());
		assertTrue("Wrong bundle in closure", closure.contains(tb2));

		// Test fragment, this pulls in the host which pulls in everything else
		closure = frameworkWiring.getDependencyClosure(Arrays.asList(new Bundle[]{tb4}));
		assertEquals("Wrong number in closure", 5, closure.size());
		assertTrue("Wrong bundles in closure: " + closure, closure.containsAll(testBundles));
		assertTrue("Wrong bundles in closure: " + closure, testBundles.containsAll(closure));
	}

	public void testRemovalPending() {
		Bundle tb1 = install("resolver.tb1.v110.jar");
		Bundle tb2 = install("resolver.tb2.jar");
		Bundle tb3 = install("resolver.tb3.jar");
		Bundle tb4 = install("resolver.tb4.jar");
		Bundle tb5 = install("resolver.tb5.jar");

		Collection testBundles = Arrays.asList(new Bundle[]{tb1, tb2, tb3, tb4, tb5});
		assertTrue(frameworkWiring.resolveBundles(testBundles));

		try {
			tb1.uninstall();
		} catch (BundleException e) {
			fail("Failed to uninstall bundle", e);
		}

		Collection removals = frameworkWiring.getRemovalPendingBundles();
		assertEquals("Wrong number of removals", 1, removals.size());
		assertTrue("Wrong bundle in removals", removals.contains(tb1));

		Collection closure = frameworkWiring.getDependencyClosure(removals);
		assertEquals("Wrong number in closure", 5, closure.size());
		assertTrue("Wrong bundles in closure: " + closure, closure.containsAll(testBundles));
		assertTrue("Wrong bundles in closure: " + closure, testBundles.containsAll(closure));

		try {
			tb4.uninstall();
		} catch (BundleException e) {
			fail("Failed to uninstall bundle", e);
		}

		removals = frameworkWiring.getRemovalPendingBundles();
		assertEquals("Wrong number of removals", 2, removals.size());
		assertTrue("Wrong bundle in removals", removals.contains(tb1));
		assertTrue("Wrong bundle in removals", removals.contains(tb4));

		closure = frameworkWiring.getDependencyClosure(removals);
		assertEquals("Wrong number in closure", 5, closure.size());
		assertTrue("Wrong bundles in closure: " + closure, closure.containsAll(testBundles));
		assertTrue("Wrong bundles in closure: " + closure, testBundles.containsAll(closure));
	}
}