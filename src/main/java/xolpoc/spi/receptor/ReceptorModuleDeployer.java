/*
 * Copyright 2015 the original author or authors.
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

package xolpoc.spi.receptor;

import io.pivotal.receptor.client.ReceptorClient;
import io.pivotal.receptor.commands.ActualLRPResponse;
import io.pivotal.receptor.commands.DesiredLRPCreateRequest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;
import org.springframework.xd.module.ModuleDescriptor;

import xolpoc.spi.ModuleDeployer;

/**
 * @author Mark Fisher
 */
public class ReceptorModuleDeployer implements ModuleDeployer {

	public static final String DOCKER_PATH = "docker:///springxd/xol-poc";

	public static final String BASE_ADDRESS = "192.168.11.11.xip.io";

	public static final String ADMIN_GUID = "xd-admin";

	private static final String MODULE_JAR_PATH = "/opt/xd/lib/xolpoc-0.0.1-SNAPSHOT.jar";

	private final ReceptorClient receptorClient = new ReceptorClient();

	@Override
	public void deploy(ModuleDescriptor descriptor) {
		String guid = guid(descriptor);
		DesiredLRPCreateRequest request = new DesiredLRPCreateRequest();
		request.setProcessGuid(guid);
		request.setRootfs(DOCKER_PATH);
		request.runAction.setPath("java");
		request.runAction.addArg("-Dmodule=" + path(descriptor));
		request.runAction.addArg("-Dspring.redis.host=" + System.getProperty("spring.redis.host"));
		request.runAction.addArg("-Dserver.port=500" + descriptor.getIndex());
		request.runAction.addArg("-jar");
		request.runAction.addArg(MODULE_JAR_PATH);
		request.setPorts(new int[] {8080, 9000});
		request.addRoute(8080, new String[] {guid + "." + BASE_ADDRESS, guid + "-8080." + BASE_ADDRESS});
		request.addRoute(9000, new String[] {guid + "-9000." + BASE_ADDRESS});
		receptorClient.createLongRunningProcess(request);		
	}

	@Override
	public void undeploy(ModuleDescriptor descriptor) {
		receptorClient.destroyLongRunningProcess(guid(descriptor));
	}

	@Override
	public List<String> getStates(ModuleDescriptor descriptor) {
		List<String> results = new ArrayList<String>();
		List<ActualLRPResponse> lrps = receptorClient.findLongRunningProcesses(guid(descriptor));
		for (ActualLRPResponse lrp : lrps) {
			StringBuilder moduleStatus = new StringBuilder(descriptor.getModuleName() + ":" + lrp.getState());
			if (StringUtils.hasText(lrp.getAddress())) {
				moduleStatus.append("@" + lrp.getAddress());
				if (StringUtils.hasText(lrp.getInstanceGuid())) {
					moduleStatus.append("/" + lrp.getInstanceGuid());
				}
			}
			results.add(moduleStatus.toString());
		}
		return results;
	}

	private String guid(ModuleDescriptor descriptor) {
		return "xd-" + descriptor.getGroup() + "-" + descriptor.getModuleName() + "-" + descriptor.getIndex();
	}

	private String path(ModuleDescriptor descriptor) {
		return descriptor.getGroup() + "." + descriptor.getType() + "." + descriptor.getModuleName() + "." + descriptor.getIndex();
	}

}