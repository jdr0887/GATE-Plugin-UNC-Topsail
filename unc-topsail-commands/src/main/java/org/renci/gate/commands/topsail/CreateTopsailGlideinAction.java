package org.renci.gate.commands.topsail;

import java.io.File;

import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;
import org.renci.jlrm.JLRMException;
import org.renci.jlrm.Queue;
import org.renci.jlrm.Site;
import org.renci.jlrm.slurm.ssh.SLURMSSHJob;
import org.renci.jlrm.slurm.ssh.SLURMSSHSubmitCondorGlideinCallable;

@Command(scope = "unc-topsail", name = "create-glidein", description = "Create Glidein")
public class CreateTopsailGlideinAction extends AbstractAction {

    @Option(name = "username", required = true, multiValued = false)
    private String username;

    @Option(name = "submitHost", required = true, multiValued = false)
    private String submitHost;

    @Option(name = "queueName", required = true, multiValued = false)
    private String queueName;

    @Option(name = "collectorHost", required = true, multiValued = false)
    private String collectorHost;

    public CreateTopsailGlideinAction() {
        super();
    }

    @Override
    public Object doExecute() {
        
        Site site = new Site();
        site.setName("Topsail");
        site.setSubmitHost(submitHost);
        site.setUsername(username);

        Queue queue = new Queue();
        queue.setName(queueName);
        queue.setRunTime(5760L);
        queue.setNumberOfProcessors(8);

        File submitDir = new File("/tmp");
        try {
            SLURMSSHSubmitCondorGlideinCallable callable = new SLURMSSHSubmitCondorGlideinCallable();
            callable.setCollectorHost(collectorHost);
            callable.setUsername(System.getProperty("user.name"));
            callable.setSite(site);
            callable.setJobName("glidein");
            callable.setQueue(queue);
            callable.setSubmitDir(submitDir);
            callable.setRequiredMemory(40);
            callable.setHostAllowRead("*.unc.edu");
            callable.setHostAllowWrite("*.unc.edu");
            SLURMSSHJob job = callable.call();
            System.out.println(job.getId());
        } catch (JLRMException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSubmitHost() {
        return submitHost;
    }

    public void setSubmitHost(String submitHost) {
        this.submitHost = submitHost;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getCollectorHost() {
        return collectorHost;
    }

    public void setCollectorHost(String collectorHost) {
        this.collectorHost = collectorHost;
    }

}
