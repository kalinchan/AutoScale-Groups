/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package fish.payara.extensions.autoscale.groups.core.admin;

import fish.payara.extensions.autoscale.groups.ScalingGroup;
import fish.payara.extensions.autoscale.groups.ScalingGroups;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author Andrew Pielage
 */
@Service(name = "list-scaling-groups")
@PerLookup
@ExecuteOn(RuntimeType.DAS)
@CommandLock(CommandLock.LockType.NONE)
@RestEndpoints({
        @RestEndpoint(configBean = ScalingGroups.class,
                opType = RestEndpoint.OpType.GET,
                path = "list-scaling-groups",
                description = "Lists configured AutoScale Groups")
})
public class ListScalingGroupsCommand implements AdminCommand {

    @Inject
    private ScalingGroups scalingGroups;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        if (scalingGroups == null) {
            adminCommandContext.getActionReport().setMessage("No scaling groups found");
            return;
        }

        ActionReport actionReport = adminCommandContext.getActionReport();
        Properties extraProperties = new Properties();
        List<Map<String, String>> scalingGroupsInfo = new ArrayList<>();
        for (ScalingGroup scalingGroup : scalingGroups.getScalingGroups()) {
            Map<String, String> scalingGroupInfo = new HashMap<>();
            scalingGroupInfo.put("name", scalingGroup.getName());
            scalingGroupInfo.put("configRef", scalingGroup.getConfigRef());
            scalingGroupInfo.put("deploymentGroupRef", scalingGroup.getDeploymentGroupRef());
            scalingGroupsInfo.add(scalingGroupInfo);

            actionReport.appendMessage(scalingGroup.getName() + "\n");
        }

        extraProperties.put("scalingGroups", scalingGroupsInfo);
        actionReport.setExtraProperties(extraProperties);
    }
}
