/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.tests.composites;

import java.util.*;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.osgi.tests.OSGiTestsActivator;
import org.osgi.framework.*;
import org.osgi.service.framework.CompositeBundle;
import org.osgi.service.framework.CompositeBundleFactory;
import org.osgi.util.tracker.ServiceTracker;

public class CompositeShareTests extends AbstractCompositeTests {
	public static Test suite() {
		return new TestSuite(CompositeShareTests.class);
	}

	public void testCompositeShare01a() {
		// simple test to create an empty composite bundle
		// sharing one package from the parent
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare01a"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "org.osgi.service.application"); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare01a", null, linkManifest, false, false); //$NON-NLS-1$
		startCompositeBundle(compositeBundle, false);
		CompositeBundle companion = compositeBundle.getCompanionComposite();
		assertNotNull("Companion is null", companion); //$NON-NLS-1$
		try {
			companion.loadClass("org.osgi.service.application.ApplicationDescriptor"); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			fail("Unexpected class load exception", e); //$NON-NLS-1$
		}
		// make sure missing classes do not endlessly loop
		try {
			companion.loadClass("does.not.exist.Here"); //$NON-NLS-1$
			fail("Expected a loadClass exception"); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			// expected
		}
		uninstallCompositeBundle(compositeBundle);
	}

	public void testCompositeShare01b() {
		// simple test to create an empty composite bundle
		// sharing one package from the parent
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare01b"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "org.osgi.service.application"); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare01a", null, linkManifest, false, false); //$NON-NLS-1$
		startCompositeBundle(compositeBundle, false);
		CompositeBundle companion = compositeBundle.getCompanionComposite();
		assertNotNull("Companion is null", companion); //$NON-NLS-1$
		uninstallCompositeBundle(companion);
		assertEquals("Composite is not in the UNINSTALLED state", Bundle.UNINSTALLED, compositeBundle.getState()); //$NON-NLS-1$
	}

	public void testCompositeShare02() {
		// simple test to create an empty composite bundle
		// sharing one package from the parent and sharing one non-existing package from child
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare02"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "org.osgi.service.application"); //$NON-NLS-1$
		linkManifest.put(Constants.EXPORT_PACKAGE, "foo"); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare02", null, linkManifest, false, false); //$NON-NLS-1$

		startCompositeBundle(compositeBundle, true);
		CompositeBundle companion = compositeBundle.getCompanionComposite();
		assertNotNull("Companion is null", companion); //$NON-NLS-1$
		try {
			companion.loadClass("org.osgi.service.application.ApplicationDescriptor"); //$NON-NLS-1$
			fail("Expected a loadClass exception"); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			// expected
		}
		uninstallCompositeBundle(compositeBundle);
	}

	public void testCompositeShare03() {
		// create a composite bundle with one bundle that exports some api to parent
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare03"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.EXPORT_PACKAGE, "test.link.a; attr1=\"value1\"; uses:=\"org.osgi.framework, test.link.a.params\", test.link.a.params; attr2=\"value2\""); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare03", null, linkManifest, false, false); //$NON-NLS-1$
		installIntoChild(compositeBundle.getCompanionFramework(), "test.link.a"); //$NON-NLS-1$

		startCompositeBundle(compositeBundle, false);
		try {
			compositeBundle.loadClass("test.link.a.SomeAPI"); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			fail("Unexpected a loadClass exception", e); //$NON-NLS-1$
		}
		//make sure missing classes do not endlessly loop
		try {
			compositeBundle.loadClass("does.not.exist.Here"); //$NON-NLS-1$
			fail("Expected a loadClass exception"); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			// expected
		}
		uninstallCompositeBundle(compositeBundle);
	}

	public void testCompositeShare04() {
		// create a composite bundle with one bundle that exports some api to parent
		// install one bundle into parent that uses API from child
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare04"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.EXPORT_PACKAGE, "test.link.a; attr1=\"bad value\"; uses:=\"org.osgi.framework, test.link.a.params\", test.link.a.params; attr2=\"bad value\""); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare04", null, linkManifest, false, false); //$NON-NLS-1$
		installIntoChild(compositeBundle.getCompanionFramework(), "test.link.a"); //$NON-NLS-1$
		Bundle testClient = installIntoCurrent("test.link.a.client"); //$NON-NLS-1$

		CompositeBundle companion = compositeBundle.getCompanionComposite();
		assertNotNull("Companion is null", companion); //$NON-NLS-1$
		startCompositeBundle(compositeBundle, true);
		try {
			testClient.start();
			fail("Expected start failure"); //$NON-NLS-1$
		} catch (BundleException e) {
			assertEquals("Unexpected exception type", BundleException.RESOLVE_ERROR, e.getType()); //$NON-NLS-1$
		}
		// put good value back
		linkManifest.put(Constants.EXPORT_PACKAGE, "test.link.a; attr1=\"value1\"; uses:=\"org.osgi.framework, test.link.a.params\", test.link.a.params; attr2=\"value2\""); //$NON-NLS-1$
		try {
			compositeBundle.update(linkManifest);
		} catch (BundleException e) {
			fail("Unexpected composite update exception", e); //$NON-NLS-1$
		}

		startCompositeBundle(compositeBundle, false);
		try {
			testClient.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}

		// put bad value back
		linkManifest.put(Constants.EXPORT_PACKAGE, "test.link.a; attr1=\"bad value\"; uses:=\"org.osgi.framework, test.link.a.params\", test.link.a.params; attr2=\"bad value\""); //$NON-NLS-1$
		try {
			compositeBundle.update(linkManifest);
		} catch (BundleException e) {
			fail("Unexpected composite update exception", e); //$NON-NLS-1$
		}
		installer.refreshPackages(new Bundle[] {compositeBundle});
		startCompositeBundle(compositeBundle, true);
		try {
			testClient.start();
			fail("Expected start failure"); //$NON-NLS-1$
		} catch (BundleException e) {
			assertEquals("Unexpected exception type", BundleException.RESOLVE_ERROR, e.getType()); //$NON-NLS-1$
		}

		uninstallCompositeBundle(compositeBundle);
	}

	public void testCompositeShare05() {
		// create a composite bundle with one bundle that exports some api to child
		// install one bundle into child that uses API from parent
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare05"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.a; attr1=\"bad value\", test.link.a.params;  attr2=\"bad value\""); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare03", null, linkManifest, false, false); //$NON-NLS-1$
		installIntoCurrent("test.link.a"); //$NON-NLS-1$
		Bundle testClient = installIntoChild(compositeBundle.getCompanionFramework(), "test.link.a.client"); //$NON-NLS-1$

		CompositeBundle companion = compositeBundle.getCompanionComposite();
		assertNotNull("Companion is null", companion); //$NON-NLS-1$
		startCompositeBundle(compositeBundle, true);
		try {
			testClient.start();
			fail("Expected start failure"); //$NON-NLS-1$
		} catch (BundleException e) {
			assertEquals("Unexpected exception type", BundleException.RESOLVE_ERROR, e.getType()); //$NON-NLS-1$
		}

		// put good value back
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.a; attr1=\"value1\", test.link.a.params; attr2=\"value2\""); //$NON-NLS-1$
		try {
			compositeBundle.update(linkManifest);
		} catch (BundleException e) {
			fail("Unexpected composite update exception", e); //$NON-NLS-1$
		}

		startCompositeBundle(compositeBundle, false);
		try {
			testClient.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}

		// put bad value back
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.a; attr1=\"bad value\", test.link.a.params;  attr2=\"bad value\""); //$NON-NLS-1$
		try {
			compositeBundle.update(linkManifest);
		} catch (BundleException e) {
			fail("Unexpected composite update exception", e); //$NON-NLS-1$
		}
		installer.refreshPackages(new Bundle[] {compositeBundle});
		startCompositeBundle(compositeBundle, true);
		try {
			testClient.start();
			fail("Expected start failure"); //$NON-NLS-1$
		} catch (BundleException e) {
			assertEquals("Unexpected exception type", BundleException.RESOLVE_ERROR, e.getType()); //$NON-NLS-1$
		}

		uninstallCompositeBundle(compositeBundle);
	}

	public void testCompositeShare06() {
		// simple test to create an empty composite bundle
		// sharing one package from a bundle in the parent and 
		// one package from the system bundle in the parent
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare06"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "org.osgi.service.application, org.osgi.framework"); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare06", null, linkManifest, false, false); //$NON-NLS-1$
		startCompositeBundle(compositeBundle, false);
		CompositeBundle companion = compositeBundle.getCompanionComposite();
		assertNotNull("Companion is null", companion); //$NON-NLS-1$
		try {
			companion.loadClass("org.osgi.service.application.ApplicationDescriptor"); //$NON-NLS-1$
			Class bundleClass = companion.loadClass("org.osgi.framework.Bundle"); //$NON-NLS-1$
			assertEquals("Bundle classes are not the same", Bundle.class, bundleClass); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			fail("Unexpected class load exception", e); //$NON-NLS-1$
		}

		uninstallCompositeBundle(compositeBundle);
	}

	public void testCompositeShare07() {
		// test two way sharing
		// create a composite bundle with one bundle that exports some api to child
		// install one bundle into child that uses API from parent
		// install a child bundle that exports some api to parent
		// install one bundle into parent that uses API from child
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare07"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.a; attr1=\"value1\", test.link.a.params; attr2=\"value2\""); //$NON-NLS-1$
		linkManifest.put(Constants.EXPORT_PACKAGE, "test.link.b; attr1=\"value1\"; uses:=\"org.osgi.framework, test.link.b.params\", test.link.b.params; attr2=\"value2\""); //$NON-NLS-1$
		CompositeBundle compositeBundle = createCompositeBundle(linkBundleFactory, "testCompositeShare07", null, linkManifest, false, false); //$NON-NLS-1$
		installIntoCurrent("test.link.a"); //$NON-NLS-1$
		Bundle testClientA = installIntoChild(compositeBundle.getCompanionFramework(), "test.link.a.client"); //$NON-NLS-1$
		installIntoChild(compositeBundle.getCompanionFramework(), "test.link.b"); //$NON-NLS-1$
		Bundle testClientB = installIntoCurrent("test.link.b.client"); //$NON-NLS-1$

		startCompositeBundle(compositeBundle, false);
		try {
			testClientA.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}

		try {
			testClientB.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}

		// use bad import package value
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.a; attr1=\"bad value\", test.link.a.params;  attr2=\"bad value\""); //$NON-NLS-1$
		try {
			compositeBundle.update(linkManifest);
		} catch (BundleException e) {
			fail("Unexpected composite update exception", e); //$NON-NLS-1$
		}
		installer.refreshPackages(new Bundle[] {compositeBundle});
		startCompositeBundle(compositeBundle, true);
		try {
			testClientA.start();
			fail("Expected start failure"); //$NON-NLS-1$
		} catch (BundleException e) {
			assertEquals("Unexpected exception type", BundleException.RESOLVE_ERROR, e.getType()); //$NON-NLS-1$
		}
		try {
			testClientB.start();
			fail("Expected start failure"); //$NON-NLS-1$
		} catch (BundleException e) {
			assertEquals("Unexpected exception type", BundleException.RESOLVE_ERROR, e.getType()); //$NON-NLS-1$
		}

		// put good value back
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.a; attr1=\"value1\", test.link.a.params; attr2=\"value2\""); //$NON-NLS-1$
		try {
			compositeBundle.update(linkManifest);
		} catch (BundleException e) {
			fail("Unexpected composite update exception", e); //$NON-NLS-1$
		}

		startCompositeBundle(compositeBundle, false);
		try {
			testClientA.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}

		try {
			testClientB.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}
		uninstallCompositeBundle(compositeBundle);
	}

	public void testCompositeShare08() {
		// Create a child framework to install composites into to test persistence
		CompositeBundle compositeLevel0 = createCompositeBundle(linkBundleFactory, "testCompositeShare08LV0", null, null, true, false); //$NON-NLS-1$
		CompositeBundleFactory factoryLevel1 = getFactory(compositeLevel0.getCompanionFramework());
		// create a level 1 composites
		CompositeBundle compositeLevel1 = createCompositeBundle(factoryLevel1, "testCompositeShare08LV1", null, null, true, false); //$NON-NLS-1$
		CompositeBundleFactory factoryLevel2 = getFactory(compositeLevel1.getCompanionFramework());
		long level1ID = compositeLevel1.getBundleId();

		// test two way sharing
		// create a composite bundle with one bundle that exports some api to child
		// install one bundle into child that uses API from parent
		// install a child bundle that exports some api to parent
		// install one bundle into parent that uses API from child
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare08LV2"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.a; attr1=\"value1\", test.link.a.params; attr2=\"value2\""); //$NON-NLS-1$
		linkManifest.put(Constants.EXPORT_PACKAGE, "test.link.b; attr1=\"value1\"; uses:=\"org.osgi.framework, test.link.b.params\", test.link.b.params; attr2=\"value2\""); //$NON-NLS-1$
		CompositeBundle compositeLevel2 = createCompositeBundle(factoryLevel2, "testCompositeShare08LV2", null, linkManifest, false, false); //$NON-NLS-1$
		long level2ID = compositeLevel2.getBundleId();

		installIntoChild(compositeLevel1.getCompanionFramework(), "test.link.a"); //$NON-NLS-1$
		Bundle testClientA = installIntoChild(compositeLevel2.getCompanionFramework(), "test.link.a.client"); //$NON-NLS-1$
		long clientAID = testClientA.getBundleId();
		installIntoChild(compositeLevel2.getCompanionFramework(), "test.link.b"); //$NON-NLS-1$
		Bundle testClientB = installIntoChild(compositeLevel1.getCompanionFramework(), "test.link.b.client"); //$NON-NLS-1$
		long clientBID = testClientB.getBundleId();

		startCompositeBundle(compositeLevel2, false);
		try {
			testClientA.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}

		try {
			testClientB.start();
		} catch (BundleException e) {
			fail("Unexpected exception", e); //$NON-NLS-1$
		}

		// stop level 0 composite
		stopCompositeBundle(compositeLevel0);

		// check the state of bundles installed into composite frameworks
		assertEquals("Wrong state for client A", Bundle.UNINSTALLED, testClientA.getState()); //$NON-NLS-1$
		assertEquals("Wrong state for client B", Bundle.UNINSTALLED, testClientB.getState()); //$NON-NLS-1$

		// start level 0 composite again
		startCompositeBundle(compositeLevel0, false);

		// get reified level 1 and 2 composites and client A and B bundles 
		compositeLevel1 = (CompositeBundle) compositeLevel0.getCompanionFramework().getBundleContext().getBundle(level1ID);
		compositeLevel2 = (CompositeBundle) compositeLevel1.getCompanionFramework().getBundleContext().getBundle(level2ID);
		testClientA = compositeLevel2.getCompanionFramework().getBundleContext().getBundle(clientAID);
		testClientB = compositeLevel1.getCompanionFramework().getBundleContext().getBundle(clientBID);

		// check the state of bundles installed into composite frameworks
		assertEquals("Wrong state for client A", Bundle.ACTIVE, testClientA.getState()); //$NON-NLS-1$
		assertEquals("Wrong state for client B", Bundle.ACTIVE, testClientB.getState()); //$NON-NLS-1$

		uninstallCompositeBundle(compositeLevel0);
	}

	public void testCompositeShare09a() {
		// exports packages from a bundle installed into the parent
		// and imports the packages from a bundle installed into the child
		// and services registered in the parent are propagated to the child
		Bundle cService = installIntoCurrent("test.link.c"); //$NON-NLS-1$
		try {
			cService.start();
		} catch (BundleException e) {
			fail("Failed to start test.link.c", e); //$NON-NLS-1$
		}
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare09a"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.c.service1,test.link.c.service2,test.link.c.service3"); //$NON-NLS-1$
		linkManifest.put(CompositeBundleFactory.COMPOSITE_SERVICE_FILTER_IMPORT, "(objectClass=test.link.c.service1.Service1),(objectClass=test.link.c.service2.Service2),(objectClass=test.link.c.service3.Service3)"); //$NON-NLS-1$
		CompositeBundle composite = createCompositeBundle(linkBundleFactory, "testCompositeShare09a", null, linkManifest, true, false); //$NON-NLS-1$

		startCompositeBundle(composite, false);

		ServiceReference[] targetServices = composite.getCompanionComposite().getRegisteredServices();
		assertNotNull("No target services found", targetServices); //$NON-NLS-1$
		assertEquals("Wrong number of target services", 3, targetServices.length); //$NON-NLS-1$

		Bundle cClient = installIntoChild(composite.getCompanionFramework(), "test.link.c.client"); //$NON-NLS-1$

		try {
			cClient.start();
			cClient.stop();
		} catch (BundleException e) {
			fail("Failed to start/stop the client", e); //$NON-NLS-1$
		}

		try {
			cService.stop();
		} catch (BundleException e) {
			fail("Failed to stop cService", e); //$NON-NLS-1$
		}

		try {
			cClient.start();
			fail("Should have failed to start cClient"); //$NON-NLS-1$
		} catch (BundleException e) {
			// expected
		}
		try {
			cService.start();
		} catch (BundleException e) {
			fail("Failed to start test.link.c", e); //$NON-NLS-1$
		}
		try {
			cClient.start();
			cClient.stop();
		} catch (BundleException e) {
			fail("Failed to start/stop the client", e); //$NON-NLS-1$
		}
		uninstallCompositeBundle(composite);
	}

	public void testCompositeShare09b() {
		// exports packages from a bundle installed into the parent
		// and imports the packages from a bundle installed into the child
		// and services registered in the parent are propagated to the child
		Bundle cService = installIntoCurrent("test.link.c"); //$NON-NLS-1$
		try {
			cService.start();
		} catch (BundleException e) {
			fail("Failed to start test.link.c", e); //$NON-NLS-1$
		}
		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare09b"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(Constants.IMPORT_PACKAGE, "test.link.c.service1,test.link.c.service2,test.link.c.service3"); //$NON-NLS-1$
		linkManifest.put(Constants.EXPORT_PACKAGE, "test.link.e.service1; attr1=\"value1\", test.link.e.service2; attr2=\"value2\"; uses:=\"test.link.e.service1\", test.link.e.service3; attr3=\"value3\"; uses:=\"test.link.e.service2\""); //$NON-NLS-1$
		linkManifest.put(CompositeBundleFactory.COMPOSITE_SERVICE_FILTER_IMPORT, "(objectClass=test.link.c.service1.Service1),(objectClass=test.link.c.service2.Service2),(objectClass=test.link.c.service3.Service3)"); //$NON-NLS-1$
		linkManifest.put(CompositeBundleFactory.COMPOSITE_SERVICE_FILTER_EXPORT, "(objectClass=test.link.e.service1.Service1),(objectClass=test.link.e.service2.Service2),(objectClass=test.link.e.service3.Service3)"); //$NON-NLS-1$
		CompositeBundle composite = createCompositeBundle(linkBundleFactory, "testCompositeShare09b", null, linkManifest, false, false); //$NON-NLS-1$

		Bundle eService = installIntoChild(composite.getCompanionFramework(), "test.link.e"); //$NON-NLS-1$
		startCompositeBundle(composite, false);

		try {
			eService.start();
		} catch (BundleException e) {
			fail("Failed to start test.link.e", e); //$NON-NLS-1$
		}

		ServiceReference[] childServices = composite.getCompanionComposite().getRegisteredServices();
		assertNotNull("No child services found", childServices); //$NON-NLS-1$
		assertEquals("Wrong number of child services", 3, childServices.length); //$NON-NLS-1$

		ServiceReference[] parentServices = composite.getRegisteredServices();
		assertNotNull("No parent services found", parentServices); //$NON-NLS-1$
		assertEquals("Wrong number of parent services", 3, parentServices.length); //$NON-NLS-1$

		Bundle cClient = installIntoChild(composite.getCompanionFramework(), "test.link.c.client"); //$NON-NLS-1$
		Bundle eClient = installIntoCurrent("test.link.e.client"); //$NON-NLS-1$

		try {
			cClient.start();
			cClient.stop();
		} catch (BundleException e) {
			fail("Failed to start/stop the client", e); //$NON-NLS-1$
		}

		try {
			cService.stop();
		} catch (BundleException e) {
			fail("Failed to stop cService", e); //$NON-NLS-1$
		}

		try {
			cClient.start();
			fail("Should have failed to start cClient"); //$NON-NLS-1$
		} catch (BundleException e) {
			// expected
		}
		try {
			cService.start();
		} catch (BundleException e) {
			fail("Failed to start test.link.c", e); //$NON-NLS-1$
		}
		try {
			cClient.start();
			cClient.stop();
		} catch (BundleException e) {
			fail("Failed to start/stop the client", e); //$NON-NLS-1$
		}

		try {
			eClient.start();
			eClient.stop();
		} catch (BundleException e) {
			fail("Failed to start/stop the client", e); //$NON-NLS-1$
		}

		try {
			eService.stop();
		} catch (BundleException e) {
			fail("Failed to stop eService", e); //$NON-NLS-1$
		}

		try {
			eClient.start();
			fail("Should have failed to start eClient"); //$NON-NLS-1$
		} catch (BundleException e) {
			// expected
		}
		try {
			eService.start();
		} catch (BundleException e) {
			fail("Failed to start test.link.e", e); //$NON-NLS-1$
		}
		try {
			eClient.start();
			eClient.stop();
		} catch (BundleException e) {
			fail("Failed to start/stop the client", e); //$NON-NLS-1$
		}
		uninstallCompositeBundle(composite);
	}

	public void testCompositeShare10() {
		// test service listeners with services shared to child
		String filter1 = "(key1=1)"; //$NON-NLS-1$
		String filter2 = "(key2=value1)"; //$NON-NLS-1$
		String filter3 = "(key3=true)"; //$NON-NLS-1$

		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare10"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(CompositeBundleFactory.COMPOSITE_SERVICE_FILTER_IMPORT, filter1 + ',' + filter2);
		CompositeBundle composite = createCompositeBundle(linkBundleFactory, "testCompositeShare10", null, linkManifest, true, false); //$NON-NLS-1$

		startCompositeBundle(composite, false);

		Hashtable props = new Hashtable();
		props.put("key1", new Integer(1)); //$NON-NLS-1$
		props.put("key2", "value1"); //$NON-NLS-1$//$NON-NLS-2$
		props.put("key3", Boolean.TRUE); //$NON-NLS-1$

		// install listener to test with before registering the service so we can get the REGISTERED event
		TestServiceListener resultListener = new TestServiceListener();
		try {
			composite.getCompanionFramework().getBundleContext().addServiceListener(resultListener, "(&" + filter1 + filter2 + filter3 + ")"); //$NON-NLS-1$//$NON-NLS-2$
		} catch (InvalidSyntaxException e) {
			fail("failed to add listener", e); //$NON-NLS-1$
		}

		// register a service that should match the listener filter
		ServiceRegistration registration = OSGiTestsActivator.getContext().registerService(new String[] {Object.class.getName()}, new Object(), props);
		try {
			// expecting to have gotten 1 REGISTERED event
			int[] results = resultListener.getResults();
			assertEquals("Wrong listener results", new int[] {1, 0, 0, 0}, results); //$NON-NLS-1$

			ServiceReference[] targetServices = composite.getCompanionComposite().getRegisteredServices();
			assertNotNull("No target services found", targetServices); //$NON-NLS-1$
			assertEquals("Wrong number of target services", 1, targetServices.length); //$NON-NLS-1$

			// modify the service properties so they match both the link description
			// and the test listener filter
			props.put("key4", "value1"); //$NON-NLS-1$ //$NON-NLS-2$
			registration.setProperties(props);
			// expecting to get a MODIFIED event
			assertEquals("Wrong listener results", new int[] {1, 1, 0, 0}, results); //$NON-NLS-1$

			// modify the service properties so they match the link description
			// but not the test listener filter
			props.put("key3", Boolean.FALSE); //$NON-NLS-1$
			registration.setProperties(props);
			// expecting to get a MODIFIED_ENDMATCH event
			assertEquals("Wrong listener results", new int[] {1, 1, 1, 0}, results); //$NON-NLS-1$

			// modify the service properties so they still match teh link description
			// but still do not match the test listener filter
			props.put("key4", "value2"); //$NON-NLS-1$ //$NON-NLS-2$
			registration.setProperties(props);
			// expecting no new events
			assertEquals("Wrong listener results", new int[] {1, 1, 1, 0}, results); //$NON-NLS-1$

			// modify the service properties so they match the link description
			// and again match the test listener filter
			props.put("key3", Boolean.TRUE); //$NON-NLS-1$
			registration.setProperties(props);
			// expecting to get a MODIFIED event
			assertEquals("Wrong listener results", new int[] {1, 2, 1, 0}, results); //$NON-NLS-1$

			// modify the service properties so they do not match the link description
			// or the test listener filter
			props.put("key1", new Integer(2)); //$NON-NLS-1$
			props.put("key2", "value2"); //$NON-NLS-1$ //$NON-NLS-2$
			registration.setProperties(props);
			// expecting to get an UNREGISTERING event 
			assertEquals("Wrong listener results", new int[] {1, 2, 1, 1}, results); //$NON-NLS-1$

		} finally {
			composite.getCompanionFramework().getBundleContext().removeServiceListener(resultListener);
			if (registration != null)
				registration.unregister();
		}

		uninstallCompositeBundle(composite);
	}

	public void testCompositeShare11() {
		// Test service trackers with shared services to child
		String filter1 = "(key1=1)"; //$NON-NLS-1$
		String filter2 = "(key2=value1)"; //$NON-NLS-1$
		String filter3 = "(key3=true)"; //$NON-NLS-1$

		Map linkManifest = new HashMap();
		linkManifest.put(Constants.BUNDLE_SYMBOLICNAME, "testCompositeShare11"); //$NON-NLS-1$
		linkManifest.put(Constants.BUNDLE_VERSION, "1.0.0"); //$NON-NLS-1$
		linkManifest.put(CompositeBundleFactory.COMPOSITE_SERVICE_FILTER_IMPORT, filter1 + ',' + filter2);
		CompositeBundle composite = createCompositeBundle(linkBundleFactory, "testCompositeShare10", null, linkManifest, true, false); //$NON-NLS-1$

		startCompositeBundle(composite, false);

		Hashtable props = new Hashtable();
		props.put("key1", new Integer(1)); //$NON-NLS-1$
		props.put("key2", "value1"); //$NON-NLS-1$//$NON-NLS-2$
		props.put("key3", Boolean.TRUE); //$NON-NLS-1$

		// open tracker to test with before registering the service so we can get the REGISTERED event
		ServiceTracker tracker = null;
		TestTrackerCustomizer resultListener = new TestTrackerCustomizer();
		try {
			tracker = new ServiceTracker(composite.getCompanionFramework().getBundleContext(), composite.getCompanionFramework().getBundleContext().createFilter("(&" + filter1 + filter2 + filter3 + ")"), resultListener); //$NON-NLS-1$ //$NON-NLS-2$
			tracker.open();
		} catch (InvalidSyntaxException e) {
			fail("failed to add listener", e); //$NON-NLS-1$
		}

		// register a service that should match the listener filter
		ServiceRegistration registration = OSGiTestsActivator.getContext().registerService(new String[] {Object.class.getName()}, new Object(), props);
		try {
			// expecting to have gotten 1 addingService call
			int[] results = resultListener.getResults();
			assertEquals("Wrong listener results", new int[] {1, 0, 0}, results); //$NON-NLS-1$

			ServiceReference[] targetServices = composite.getCompanionComposite().getRegisteredServices();
			assertNotNull("No target services found", targetServices); //$NON-NLS-1$
			assertEquals("Wrong number of target services", 1, targetServices.length); //$NON-NLS-1$

			// modify the service properties so they match both the link description
			// and the test tracker filter
			props.put("key4", "value1"); //$NON-NLS-1$ //$NON-NLS-2$
			registration.setProperties(props);
			// expecting to get 1 modifiedService call
			assertEquals("Wrong listener results", new int[] {1, 1, 0}, results); //$NON-NLS-1$

			// modify the service properties so they match the link description
			// but not the test tracker filter
			props.put("key3", Boolean.FALSE); //$NON-NLS-1$
			registration.setProperties(props);
			// expecting to get a removedService call
			assertEquals("Wrong listener results", new int[] {1, 1, 1}, results); //$NON-NLS-1$

			// modify the service properties so they still match the link description
			// but still do not match the test listener filter
			props.put("key4", "value2"); //$NON-NLS-1$ //$NON-NLS-2$
			registration.setProperties(props);
			// expecting no new method calls
			assertEquals("Wrong listener results", new int[] {1, 1, 1}, results); //$NON-NLS-1$

			// modify the service properties so they match the link description
			// and again match the test tracker filter
			props.put("key3", Boolean.TRUE); //$NON-NLS-1$
			registration.setProperties(props);
			// expecting to get an addingService call
			assertEquals("Wrong listener results", new int[] {2, 1, 1}, results); //$NON-NLS-1$

			// modify the service properties so they do not match the link description
			// or the test tracker filter
			props.put("key1", new Integer(2)); //$NON-NLS-1$
			props.put("key2", "value2"); //$NON-NLS-1$ //$NON-NLS-2$
			registration.setProperties(props);
			// expecting to get a removedService call 
			assertEquals("Wrong listener results", new int[] {2, 1, 2}, results); //$NON-NLS-1$

		} finally {
			if (tracker != null)
				tracker.close();
			if (registration != null)
				registration.unregister();
		}

		uninstallCompositeBundle(composite);
	}
}