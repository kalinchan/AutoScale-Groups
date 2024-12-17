package fish.payara.extensions.autoscale.groups.aws;

import com.sun.enterprise.config.serverbeans.Domain;
import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.extensions.autoscale.groups.Scaler;
import fish.payara.extensions.autoscale.groups.ScalerFor;
import fish.payara.extensions.autoscale.groups.ScalingGroup;
import fish.payara.extensions.autoscale.groups.core.admin.ScaleCommandHelper;
import jakarta.inject.Inject;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.internal.api.InternalSystemAdministrator;
import org.jvnet.hk2.annotations.Service;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@ScalerFor(AWSScalingGroup.class)
public class AWSScaler extends Scaler {

    @Inject
    private CommandRunner commandRunner;

    @Inject
    private InternalSystemAdministrator internalSystemAdministrator;

    private static Ec2AsyncClient ec2AsyncClient;


    @Override
    protected void validate(int numberOfInstances, ScalingGroup scalingGroup) throws CommandValidationException {
        super.validate(numberOfInstances, scalingGroup);
        AWSScalingGroup awsScalingGroup = (AWSScalingGroup) scalingGroup;
        if (awsScalingGroup.getMinInstances() < 0 ) {
            throw new CommandValidationException("Min instances must be greater than zero");
        }

        if (awsScalingGroup.getMaxInstances() < awsScalingGroup.getMinInstances() ) {
            throw new CommandValidationException("Max instances must be greater than Min instances");
        }


        Region region = Region.of(awsScalingGroup.getRegion());
        if (!Region.regions().contains(region)) {
            throw new CommandValidationException("Unknown AWS Region");
        }
        InstanceType instanceType = InstanceType.fromValue(awsScalingGroup.getInstanceType());
        if (instanceType == null) {
            throw new CommandValidationException("Unknown InstanceType");
        }

        if (awsScalingGroup.getAmiId().isBlank()) {
            throw new CommandValidationException("AWS AMI ID cannot be blank");
        }

        if (awsScalingGroup.getPayaraInstallDir().isBlank()) {
            throw new CommandValidationException("Payara Install directory cannot be blank");
        }

        if (awsScalingGroup.getPasswordFilePath().isBlank()) {
            throw new CommandValidationException("Password file path cannot be blank");
        }
    }

    /*
     * Order of this should be:
     * 1. Validate Configuration (instances, total instances <= max, aws info)
     * 2. Create AWS Instance and Security Group etc
     * 3. Create SSH Node
     * 4. Create Instance
     * 5. ... to be confirmed
     */
    @Override
    public ActionReport scaleUp(int numberOfNewInstances, ScalingGroup scalingGroup) {
        ActionReport actionReport = commandRunner.getActionReport("plain");

        try {
            validate(numberOfNewInstances, scalingGroup);
        } catch (CommandValidationException commandValidationException) {
            actionReport.setMessage("Scale up operation cancelled: an error was encountered during validation");
            actionReport.setFailureCause(commandValidationException);
            actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return actionReport;
        }
        try {
            // Create the instances (we currently fail out if we fail to create a single one)
            List<String> instanceNames = createInstances(numberOfNewInstances, scalingGroup,
                    actionReport.addSubActionsReport());
            // Attempt to start the instances
            //startInstances(instanceNames, actionReport.addSubActionsReport());
        } catch (CommandException commandException) {
            actionReport.setFailureCause(commandException);
            actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return actionReport;
        }
        return actionReport;
    }

    @Override
    public ActionReport scaleDown(int numberOfInstancesToRemove, ScalingGroup scalingGroup) {
        return null;
    }

    /**
     * Creates the requested number of instances using the {@link AWSScalingGroup scaling group} config
     *
     * @param numberOfNewInstances The number of instances to create
     * @param scalingGroup The scaling group we're creating the instances against
     * @param actionReport The action report we want to add out command outputs to
     * @return A List containing the names of all created instances
     * @throws CommandException If there's an error creating any instances.
     */
    private List<String> createInstances(int numberOfNewInstances, ScalingGroup scalingGroup,
                                         ActionReport actionReport) throws CommandException {
        List<String> instanceNames = new ArrayList<>();
        createEC2Instance(scalingGroup, numberOfNewInstances);

        return instanceNames;
    }


    private void createEC2Instance(ScalingGroup scalingGroup, int numberOfNewInstances) {
        AWSScalingGroup awsScalingGroup = (AWSScalingGroup) scalingGroup;
        RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder()
                .instanceType(awsScalingGroup.getInstanceType())
                .maxCount(numberOfNewInstances)
                .minCount(numberOfNewInstances)
                .imageId(awsScalingGroup.getAmiId())
                .build();

        CompletableFuture<RunInstancesResponse> responseFuture = getAsyncClient(awsScalingGroup.getRegion()).runInstances(runInstancesRequest);
        CompletableFuture<String> future = responseFuture.thenCompose(response -> {
            String instanceIdVal = response.instances().get(0).instanceId();
            System.out.println("Going to start an EC2 instance and use a waiter to wait for it to be in running state");
            return getAsyncClient(awsScalingGroup.getRegion()).waiter()
                    .waitUntilInstanceExists(r -> r.instanceIds(instanceIdVal))
                    .thenCompose(waitResponse -> getAsyncClient(awsScalingGroup.getRegion()).waiter()
                            .waitUntilInstanceRunning(r -> r.instanceIds(instanceIdVal))
                            .thenApply(runningResponse -> instanceIdVal));
        }).exceptionally(throwable -> {
            // Handle any exceptions that occurred during the async call
            throw new RuntimeException("Failed to run EC2 instance: " + throwable.getMessage(), throwable);
        });
    }

    private static Ec2AsyncClient getAsyncClient(String region) {
        if (ec2AsyncClient == null) {
            /*
            The `NettyNioAsyncHttpClient` class is part of the AWS SDK for Java, version 2,
            and it is designed to provide a high-performance, asynchronous HTTP client for interacting with AWS services.
             It uses the Netty framework to handle the underlying network communication and the Java NIO API to
             provide a non-blocking, event-driven approach to HTTP requests and responses.
             */
            SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                    .maxConcurrency(50)  // Adjust as needed.
                    .connectionTimeout(Duration.ofSeconds(60))  // Set the connection timeout.
                    .readTimeout(Duration.ofSeconds(60))  // Set the read timeout.
                    .writeTimeout(Duration.ofSeconds(60))  // Set the write timeout.
                    .build();

            ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofMinutes(2))  // Set the overall API call timeout.
                    .apiCallAttemptTimeout(Duration.ofSeconds(90))  // Set the individual call attempt timeout.
                    .build();

            ec2AsyncClient = Ec2AsyncClient.builder()
                    .region(Region.of(region))
                    .httpClient(httpClient)
                    .overrideConfiguration(overrideConfig)
                    .build();
        }
        return ec2AsyncClient;
    }
}
