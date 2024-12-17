package fish.payara.extensions.autoscale.groups.aws;

import fish.payara.extensions.autoscale.groups.ScalingGroup;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.Element;

@Configured
public interface AWSScalingGroup extends ScalingGroup {

    @Element("region")
    String getRegion();
    void setRegion(String region);

    @Element("instance-type")
    String getInstanceType();
    void setInstanceType(String instanceType);

    @Element("ami-id")
    String getAmiId();
    void setAmiId(String amiId);

    @Element("min-instances")
    int getMinInstances();
    void setMinInstances(int minInstances);

    @Element("max-instances")
    int getMaxInstances();
    void setMaxInstances(int maxInstances);

    @Element("payara-install-dir")
    String getPayaraInstallDir();
    void setPayaraInstallDir(String installDir);

    @Element("password-file-path")
    String getPasswordFilePath();
    void setPasswordFilePath(String passwordFilePath);
}
