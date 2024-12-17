package fish.payara.extensions.autoscale.groups.aws.admin;

import com.sun.enterprise.util.StringUtils;
import fish.payara.extensions.autoscale.groups.ScalingGroups;
import fish.payara.extensions.autoscale.groups.admin.CreateScalingGroupCommand;
import fish.payara.extensions.autoscale.groups.aws.AWSScalingGroup;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

@Service(name = "create-aws-scaling-group")
@PerLookup
@ExecuteOn(RuntimeType.DAS)
@RestEndpoints({
        @RestEndpoint(configBean = ScalingGroups.class,
                opType = RestEndpoint.OpType.POST,
                path = "create-aws-scaling-group",
                description = "Creates a AWS Scaling Group"
        )
})
public class CreateAWSScalingGroupCommand extends CreateScalingGroupCommand {


    @Param(name = "region")
    private String region;

    @Param(name = "instance-type")
    private String instanceType;

    @Param(name = "ami-id")
    private String amiId;

    @Param(name = "min-instances")
    private int minInstances;

    @Param(name = "max-instances")
    private int maxInstances;

    @Param(name = "payara-install-dir")
    private String payaraInstallDir;

    @Param(name = "password-file-path")
    private String passwordFilePath;

    
    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        try {
            validateParams();
        } catch (CommandValidationException commandValidationException) {
            adminCommandContext.getActionReport().setFailureCause(commandValidationException);
            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        try {
            ConfigSupport.apply(scalingGroupsProxy -> {
                AWSScalingGroup awsScalingGroupProxy = scalingGroupsProxy.createChild(AWSScalingGroup.class);
                awsScalingGroupProxy.setName(name);
                awsScalingGroupProxy.setDeploymentGroupRef(deploymentGroupRef);

                if (StringUtils.ok(configRef)) {
                    awsScalingGroupProxy.setConfigRef(configRef);
                }
                awsScalingGroupProxy.setRegion(region);
                awsScalingGroupProxy.setInstanceType(instanceType);
                awsScalingGroupProxy.setAmiId(amiId);
                awsScalingGroupProxy.setMinInstances(minInstances);
                awsScalingGroupProxy.setMaxInstances(maxInstances);
                awsScalingGroupProxy.setPayaraInstallDir(payaraInstallDir);
                awsScalingGroupProxy.setPasswordFilePath(passwordFilePath);

                scalingGroupsProxy.getScalingGroups().add(awsScalingGroupProxy);
                return scalingGroupsProxy;
            }, scalingGroups);
        } catch (TransactionFailure transactionFailure) {
            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            adminCommandContext.getActionReport().setFailureCause(transactionFailure);
        }
    }
}
