/*
 * Copyright (c) 2018 Behsa Corporation.
 *
 *   This file is part of Ganjex.
 *
 *    Ganjex is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    Ganjex is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with Ganjex.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.behsacorp.ganjex.api;

import java.io.File;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.behsacorp.ganjex.container.GanjexApplication;
import com.behsacorp.ganjex.container.HookLoader;
import com.behsacorp.ganjex.watch.ClassPathFileChangeListener;
import com.behsacorp.ganjex.watch.ClasspathWatcher;
import com.behsacorp.ganjex.watch.JarWatcher;
import com.behsacorp.ganjex.watch.LibraryFileChangeListener;
import com.behsacorp.ganjex.watch.ServiceFileChangeListener;
import com.behsacorp.ganjex.watch.Watcher;

/**
 * Ganjex container class. Each instance of this class represents a Ganjex container.
 * <p>
 * The static method <code>run({@link GanjexConfiguration})</code> is expected to invoke for
 * starting a new container. By running a new container, Ganjex watches the library and service
 * directory for any prospective changes. As soon as a jar file is added to or removed from the
 * directory, Ganjex would start or shutdown services pertinent to the altered jar file.
 * </p>
 *
 * @author hekmatof
 * @since 1.0
 */
public final class Ganjex {
	private static final Logger log = LoggerFactory.getLogger(Ganjex.class);
	private static volatile boolean bootstrapped = false;
	private final GanjexApplication app;
	private Watcher serviceWatcher = null;
	private Watcher classpathWatcher = null;
	private JarWatcher libWatcher = null;

	/**
	 * create a new Ganjex container, to run the container you should call <code>run</code> method
	 *
	 * @param config ganjex application configuration instance
	 */
	@SuppressWarnings("WeakerAccess")
	public Ganjex(GanjexConfiguration config) {
		app = new GanjexApplication(config);
	}

	/**
	 * start a new ganjex container and return an object of this class representing the container
	 * same as calling <code>new Ganjex(config).run()</code>
	 *
	 * @param config ganjex configuration object, this object should be created by the
	 *               {@link GanjexConfiguration.Builder} which is a builder for
	 *               {@link GanjexConfiguration}
	 * @return a running ganjex container object
	 */
	public static Ganjex run(GanjexConfiguration config) {
		return new Ganjex(config).run();
	}

	/**
	 * indicate the bootstrap process of the container has done or not
	 *
	 * @return return true if and only if container bootstrapped and not destroyed
	 */
	public static boolean bootstrapped() {
		return bootstrapped;
	}

	/**
	 * @return the top level classloader
	 */
	public ClassLoader mainClassLoader() {
		return app.mainClassLoader();
	}

	private void watchServicesDirectory() {
		classpathWatcher = new ClasspathWatcher(
				new ClassPathFileChangeListener(app), app.config().getWatcherDelay(), app.config().getClassPaths());
		serviceWatcher = new JarWatcher(new File(app.config().getServicePath()),
						new ServiceFileChangeListener(app), app.config().getWatcherDelay());
	}

	private void watchLibraryDirectory() {
		libWatcher = new JarWatcher(new File(app.config().getLibPath()),
						new LibraryFileChangeListener(app), app.config().getWatcherDelay());
	}

	/**
	 * useful for testing, destroy the container and clean all the states, also interrupt all
	 * watcher threads
	 */
	public void destroy(){
		app.destroy();
		bootstrapped = false;
		if (Objects.nonNull(serviceWatcher))
			serviceWatcher.destroy();
		if (Objects.nonNull(classpathWatcher))
			classpathWatcher.destroy();
		if (Objects.nonNull(libWatcher))
			libWatcher.destroy();
		System.gc();
		log.info("ganjex shutdown correctly");
	}

	/**
	 * run the container. start watchers on library and service directory and detect any changes
	 * there. this method has been used in the complicated scenarios, usually clients use
	 * <code>Ganjex.run({@link GanjexConfiguration})</code> static method.
	 *
	 * @return ganjex container object
	 */
	@SuppressWarnings("WeakerAccess")
	public Ganjex run() {
		watchLibraryDirectory();
		if(app.libClassLoader()==null)
			app.setLibClassLoader(mainClassLoader());
		new HookLoader(app).loadHooks();
		app.lifecycleManagement().doneRegistering();
		watchServicesDirectory();
		bootstrapped = true;
		return this;
	}

}
