/*
 * Copyright 2018 Behsa Corporation
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
 */

package com.behsa.ganjex.lifecycle;

import com.behsa.ganjex.api.ServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LifecycleManagement class is a heart of the lifecycle management of the services. All of the
 * framework hooks will register by the bootstrap process in
 * {@link com.behsa.ganjex.container.HookLoader} class. after all the registrations, the object of
 * this class is ready to handle start and shutdown of the services.
 * <p>
 * This Class Object is responsible to invoke all the startup hooks for every new service found
 * by the ganjex container, and if any service removed also {@link LifecycleManagement} is
 * responsible to invoke shutdown hooks for that service.
 * <p>
 * Just one instance of this class is enough for every container
 *
 * @author Esa Hekmatizadeh
 * @since 1.0
 */
public final class LifecycleManagement {
	private static final Logger log = LoggerFactory.getLogger(LifecycleManagement.class);
	private final Map<String, ServiceContext> services = new ConcurrentHashMap<>();
	private final List<StartupHook> startupHooks = new ArrayList<>();
	private final List<ShutdownHook> shutdownHooks = new ArrayList<>();
	private volatile boolean ready = false;

	/**
	 * register a new startup hook
	 *
	 * @param startupHook hook representation
	 * @throws IllegalStateException if called after doneRegistering
	 */
	public void registerStartupHook(StartupHook startupHook) {
		if (ready)
			throw new IllegalStateException("registering hook after startup is not supported");
		startupHooks.add(startupHook);
	}

	/**
	 * register a new shutdown hook
	 *
	 * @param shutdownHook hook representation
	 * @throws IllegalStateException if called after doneRegistering
	 */
	public void registerShutdownHook(ShutdownHook shutdownHook) {
		if (ready)
			throw new IllegalStateException("registering hook after startup is not supported");
		shutdownHooks.add(shutdownHook);
	}

	/**
	 * state that registration is done, all calls of <code>registerShutdownHook</code> and
	 * <code>registerStartupHook</code> after calling this method cause {@link IllegalStateException}
	 *
	 * @throws IllegalStateException if this method ran before
	 */
	public void doneRegistering() {
		if (ready)
			throw new IllegalStateException("call doneRegistering method more than once");
		Collections.sort(startupHooks);
		ready = true;
	}

	/**
	 * find a service context by its file name
	 *
	 * @param fileName file name of the service
	 * @return service context if found and null if nothing found
	 */
	ServiceContext findContext(String fileName) {
		return services.get(fileName);
	}

	/**
	 * register a new service with the context provided and run all the startup hooks on it
	 *
	 * @param context context of the service which want to start
	 */
	void serviceStarted(ServiceContext context) {
		services.put(context.getFileName(), context);
		startupHooks.forEach(h ->
						h.hook().accept(context));
		log.debug("all startup hooks executed for the service {} version {}", context.getName(),
						context.getVersion());
	}

	/**
	 * run all the shutdown hooks on the service specified by its context, and then remove
	 * this service from the list of services
	 *
	 * @param context context of the service which want to shutdown
	 */
	void serviceDestroyed(ServiceContext context) {
		shutdownHooks.forEach(h -> h.hook().accept(context));
		log.debug("all shutdown hooks executed for the service {} version {}", context.getName(),
						context.getVersion());
		try {
			((URLClassLoader) context.getClassLoader()).close();
		} catch (IOException e) {
			log.error("could not close service {} version {} classloader", context.getName(), context
							.getVersion());
		}
		services.remove(context.getFileName());
	}

	/**
	 * useful for testing, destroy lifecycleManagement instance and clean all the registered hooks
	 */
	public void destroy() {
		services.clear();
		startupHooks.clear();
		shutdownHooks.clear();
		ready = false;
	}

	/**
	 * return a clone of the services list
	 *
	 * @return all services registered into ganjex container
	 */
	public Collection<ServiceContext> allServices() {
		return new ArrayList<>(services.values());
	}
}
