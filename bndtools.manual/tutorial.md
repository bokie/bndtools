Bndtools Tutorial
=================

In this tutorial we will build a sample application composed of two components and an API. There will be a total of three bundles created, and in this case we will deliver all three bundles from the same project (see Section TODO for information on the pros and cons of delivering multiple bundles from a single project).

Installing Bndtools
-------------------

Bndtools requires and has been tested with Eclipse versions 3.5 through 3.7. Installation of Bndtools uses the standard Eclipse update installer; if you are an experienced Eclipse user then you will only need to know the update site URL, which is as follows:

	http://bndtools-updates.s3.amazonaws.com/

TODO: full installation instructions for newbies.

Create a New Bndtools OSGi Project
----------------------------------

First we need to create a Bndtools OSGi Project. This is just a standard Eclipse Java Project, with an additional builder for constructing OSGi bundles.

From the File menu, select New -> Bndtools OSGi Project.

![](images/newwizard_01.png)

On the next page, enter `org.example` as the name of the project. Select at least J2SE-1.5 for the JRE execution environment.

![](images/newwizard_02.png)

On the next page, you are offered a choice of static repositories to import into the workspace. These repositories host bundles that you may wish to use during development or runtime. We will use bundles from the Base repository, so leave this entry checked in the list and click Next. As this is the first time a repository has been configured, the tool will take a few seconds to copy in the contents.

![](images/newwizard_03.png)

The next page shows the bundles available in the workspace repository, which we can choose to add to the project build path. This is necessary if our project will use the APIs defined in those bundles. The list also contains "libraries", which are aggregations of bundles that are expanded upon addition. For this example, double click on the @lib.component-dev@ library to add it to the right-hand list. This library contains: an annotations API that will be useful for defining components; the OSGi core and compendium APIs; JUnit for testing.

!(images/newwizard_04.png)

Important Points:

* Bnd projects are based on standard Eclipse Java projects.
* The bundles used by the project are provided by a repository.
* The @bnd.bnd@ file in each project controls the settings for that project.

Write and Export an API
-----------------------

OSGi offers strong decoupling of producers and consumers of functionality. This is done by encouraging an API-based (or in Java terms, interface-based) programming model, where producers of functionality implement APIs and the consumers of functionality bind only to APIs, not any particular implementation. For our example we will use a fairly trivial API.

*Create the Interface*

In the @src@ directory of the new project, create a package named @org.example.api@. In the new package create a Java interface named @Greeting@, as follows:

bc.. 
package org.example.api;

public interface Greeting {
    String sayHello(String name);
}

h4. Build the API Bundle

Now we will create a bundle that exports the API. Open the New Wizard and select New Bnd Bundle Descriptor.

!images/newbundle_01.png!

Enter @api@ as the name of the bundle and click Finish.

!images/newbundle_02.png!

A pop-up dialog will ask if "sub-bundles" should be enabled on the project; answer Yes to this question. The bundle editor will open on the new file, @api.bnd@.

You will also notice that a bundle JAR has been build in the @generated@ directory, named @org.example.api.jar@. The bundle JAR is rebuilt every time its bundle descriptor (i.e., the @.bnd@ file) is changed, or when its contents change. However if you double-click on the JAR file to examine its contents, you will notice that it is empty, save for the @META-INF/MANIFEST.MF@ file and a copy of the bundle descriptor. This is because we have not told Bnd what packages to include in the bundle.

We wish to include the @org.example.api@ package in the bundle as an exported package. We can do this by selecting the Exports tab of the editor and then either dragging in the @org.example.api@ package from the Package Explorer view, or clicking the Add button and selecting the package from the pop-up dialog. Whichever way we choose, the tool then asks us for the version to be declared on the newly exported package. Select the default, which is to keep the exported package version in sync with its enclosing bundle's version.

!images/exportversion.png!

The Exports tab of the editor should now look as it does below. If you save the file, the bundle JAR will be regenerated to include the declared package.

!images/export.png!

h4. Important Points:

* Bnd projects can provide either a single or multiple bundles.
* When a Bnd project provides a single bundle, the configuration for that bundle is in the main @bnd.bnd@ file. When providing multiple bundles, the settings for each are in separate @.bnd@ files called "sub-bundles", but project-wide settings (e.g., build path) are still defined in @bnd.bnd@.
* The identity for a bundle -- known as its "Bundle Symbolic Name", or BSN -- is controlled by the Bnd project name. In the case of a single-bundle project, the BSN is equal to the project name. In the case of a multi-bundle project, the BSN for each bundle is prefixed by the project name. Therefore a sub-bundle named @api.bnd@ in the @org.example@ project results in a bundle with a BSN of @org.example.api@.
* Sub-bundle descriptors should appear at the top level of a project. They can be placed elsewhere but then the @bnd.bnd@ file must be manually edited to point to their location.
* Normally bundles contain more than just a single interface or class, but this is a trivial example!

h3. Write and Test an Implementation

We will now write a class that implements the API defined previously.

h4. Write the Implementation

Create a new package named @org.example.impl@. In that package create a class named @BasicGreeting@ with the following code:

bc.. 
package org.example.impl;

import org.example.api.Greeting;
import aQute.bnd.annotation.component.Component;

@Component
public class BasicGreeting implements Greeting {
    public String sayHello(String name) {
        return "Hello " + name;
    }
}

p. Note the use of the @Component@ annotation. This enables our bundle to use OSGi __Declarative Services__ to declare the API implementation class. This means that instances of the class will be automatically created and registered with the OSGi service registry. However the annotation is build-time only, and does not pollute our class with runtime dependencies -- in other words, this is a "Plain Old Java Object" or POJO.

h4. Test the Implementation

We should write a test case to ensure this implementation class works as expected. In the @test@ source folder, create a package named @org.example.impl@ and inside create a JUnit3 test case named @BasicGreetingTest@. Add the following code:

bc.. 
package org.example.impl;

import junit.framework.TestCase;

public class BasicGreetingTest extends TestCase {
    public void testHello() {
        assertEquals("Hello Fred", new BasicGreeting().sayHello("Fred"));
    }
}

p. Now we run the test to make sure we get a green bar before proceeding. Since this is a unit test rather than an integration test, we do not need to run an OSGi Framework, so we use the standard JUnit launcher. This is possible because the implementation class is a POJO with no dependencies on the OSGi APIs.

The simplest way to run the test is to right-click on the @BasicGreetingTest@ class and select "Run As" and then "JUnit Test".

!images/unittest.png!

h4. Build the Implementation Bundle

Now we create a bundle to contain the implementation. Using the "New Bnd Bundle Descriptor" wizard again, create @greeting-impl.bnd@ at the top level of the project. Select the Components tab of the new editor, and drag in the @BasicGreeting@ class from the Package Explorer. This results in a warning, shown near the editor title, that the @org.example.impl@ package is not included in the bundle. Click on this warning and select the first fix action, which adds the package to the bundle as a private (i.e., not exported) package.

!images/fixpackage.png!

h4. Important Points

* Components are Plain Old Java Objects (POJOs) that are declared to the OSGi __Declarative Services__ runtime. They have no dependency on the OSGi APIs.
* As a result, components can and should be unit-tested outside of the OSGi framework, for example using a conventional JUnit runtime.
* We need to tell Bnd to declare each component class; we do this by adding the class name to the Component list. We also need to ensure that the package(s) containing the component implementations are shipped in the bundle. The best way to do this is to add those packages as "private" packages.

h3. Write a Consumer Component

The consumer component for the API will be a simple Swing GUI.
